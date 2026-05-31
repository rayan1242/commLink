# CommLink — Android Push-to-Talk Communication App

A production-grade real-time PTT Android app simulating the core radio communication stack used in public safety systems — built to demonstrate the kind of engineering decisions that matter in mission-critical voice infrastructure.

## Purpose

Push-to-talk systems impose constraints that most app engineering never encounters: sub-second latency, persistent connections that must survive network flaps, audio APIs that fight with the OS for hardware priority, and session signaling that must handle preemption. This app was built to internalize those constraints: every architectural decision prioritizes correctness under failure over simplicity.

Real device testing confirmed the end-to-end stack works:

- Channel list renders with stable LazyColumn keys — no full-list recomposition on updates
- Selecting a channel sends a SIP-style INVITE over WebSocket, session state transitions to Active
- PTT button cycles through Connecting → Established → Idle with hold-to-talk gesture intact
- Text messages written to Room before WebSocket send — persisted even if send fails
- Python backend receives all frame types (binary audio, JSON control) and echoes acknowledgment
- Revoke state fires when an incoming message arrives while transmitting — button disables for 1.5s
- All 12 unit tests and 5 Espresso tests pass on a Moto G Power 5G (Android 15)

## Features

### Channel Selection with Stable LazyColumn
Channels are rendered in a `LazyColumn` with `key = { channel -> channel.id }`. Without stable keys, Compose discards and recreates every item in the list on any data change — visible as flicker on long lists. With stable keys, only changed items trigger recomposition. The channel list screen is shown first on launch; the PTT screen is gated behind channel selection. Mic permission is only requested after the user selects a channel — not on cold start.

### Push-to-Talk with Floor Control State Machine
The PTT button is a sealed class state machine with four states: `Idle`, `Connecting`, `Established`, `Revoked`. Pressing the button immediately transitions to `Connecting`, starts `AudioRecord`, then transitions to `Established` after a 500ms floor-control acknowledgment window. Releasing transitions back to `Idle`. If an incoming message arrives while `Established`, the ViewModel fires `revokePTT()` — state becomes `Revoked`, audio stops, and the button disables for 1.5s before returning to `Idle`. The button uses `Box + pointerInput(Unit)` — not `Button` — to avoid a gesture conflict explained in Key Technical Decisions.

### Real-Time Voice over WebSocket
`AudioRecord` captures 20ms PCM chunks on a background thread. Each chunk is sent as a binary WebSocket frame via OkHttp. On receive, binary frames are routed to `AudioTrack` for playback. Text frames carry JSON-encoded `PTTMessage` objects. The WebSocket connection is opened on channel join and closed on leave — one persistent connection per session, not one per message.

### SIP-Style Session Signaling
Channel join sends an `INVITE` message; channel leave sends a `BYE`. These mirror SIP session establishment and teardown, giving the server explicit lifecycle events to track participants. `PTTSessionState` is a sealed class: `Idle → Inviting → Active → Terminating → Terminated`. Every transition is tracked — there is no ambiguous "maybe connected" state.

### Offline-Durable Messaging
`PTTRepository.sendTextMessage()` writes to Room before sending over WebSocket. If the WebSocket send fails silently (connection dropped, server down), the message is already persisted locally. The same pattern applies to incoming messages — the WebSocket listener inserts to Room before emitting on `SharedFlow`. Messages survive rotation and process death.

### Network Reliability with Exponential Backoff
`onFailure` in the WebSocket listener triggers `reconnect()` with exponential backoff: `delay(RECONNECT_DELAY_MS * attempt)`. After `MAX_RETRIES` the state settles at `Disconnected`. `NetworkState` is a sealed class (`Connected`, `Disconnected`, `Reconnecting(attempt)`) — the status bar always reflects the exact reconnection attempt count, not just a binary on/off.

## Architecture

Clean Architecture with MVVM and strict layer separation:

```
Presentation  →  ViewModel  →  Repository  →  [ Room | PTTWebSocketService ]
     ↕                ↕                ↕
  Compose UI      StateFlow       AudioManager
```

- **Presentation** — Jetpack Compose screens observe `StateFlow` from `PTTViewModel`. Sealed `PTTUiState` classes (`Idle`, `Loading`, `Active`, `Error`) drive all UI states. `PTTButtonState` drives the PTT button independently.
- **Domain** — Pure Kotlin models with no Android imports. `PTTMessage`, `PTTSessionState`, `NetworkState`, `PTTButtonState` are completely decoupled from Room entities and WebSocket internals.
- **Data** — `PTTRepository` is the single source of truth. It decides ordering of Room vs WebSocket operations. `PTTWebSocketService` handles the OkHttp lifecycle. `PTTAudioManager` owns `AudioRecord`/`AudioTrack`. ViewModels never touch DAOs or the WebSocket directly.

## Tech Stack

| Layer | Technology | Why |
|-------|-----------|-----|
| UI | Jetpack Compose | Declarative, integrates with StateFlow natively |
| State | StateFlow + sealed classes | Kotlin-native, no lifecycle wrapper needed |
| Gestures | `pointerInput` + `detectTapGestures` | Detects press AND release — `onClick` only detects tap |
| Local DB | Room | Type-safe SQLite, Flow support for reactive message list |
| Networking | OkHttp WebSocket | Persistent full-duplex — no REST overhead per PTT frame |
| Audio | AudioRecord + AudioTrack | Direct PCM — lowest latency Android audio path |
| DI | Hilt | Compile-time verified, Android lifecycle-aware |
| Testing | JUnit + Mockito + Espresso | Unit + UI coverage across both test suites |
| Backend | Python `websockets` | Echo server for local integration testing |

## Key Technical Decisions

| Decision | Why |
|----------|-----|
| `Box + pointerInput` not `Button` for PTT | `Button(onClick)` installs its own `clickable` gesture handler internally. When `pointerInput` is added to the same modifier chain, both handlers compete for press events. `Button`'s handler consumed the press before `detectTapGestures` saw it — the button never detected release |
| `pointerInput(Unit)` not `pointerInput(state)` | A key change restarts the entire coroutine block. With `pointerInput(state)`, every state transition (Idle→Connecting at press, Connecting→Established 500ms later) cancelled the in-flight `tryAwaitRelease()`. The release event was lost. `pointerInput(Unit)` keeps the coroutine alive; `rememberUpdatedState` lets it read the latest state without restarting |
| `flatMapLatest` for message list | `StateFlow` is initialized once — binding `getMessages("")` at construction means it queries the wrong channel forever. `flatMapLatest` re-subscribes to the Room `Flow` whenever `_currentChannelId` changes, so the list always reflects the active channel |
| Room write before WebSocket send | If the WebSocket send fails silently, the message is already in Room. Persistence is guaranteed regardless of network state |
| `SharedFlow` for messages, `StateFlow` for network | Messages are one-time events — a new subscriber should not receive old messages. Network state is current status — a new subscriber always needs the latest value |
| Mic permission deferred until channel join | Requesting `RECORD_AUDIO` on cold start pops a system dialog before the channel list renders. That dialog covers the Compose window — Espresso cannot find the hierarchy. Deferring to post-channel-selection fixes both UX and test reliability |
| `WebSocket` not `Retrofit` | The audio pipeline requires persistent binary framing. REST would require a new HTTP connection per audio chunk — unworkable at 20ms intervals |
| SIP-style `INVITE`/`BYE` | Gives the server explicit session lifecycle events. Without signaling, the server cannot distinguish "user is idle" from "user lost connection" |

## Audio Pipeline

```
AudioRecord (mic)
  └─ 20ms PCM ByteArray chunks
       └─ OkHttp WebSocket binary frame (sendAudioChunk)
            └─ Backend broadcasts
                 └─ WebSocket binary frame received
                      └─ AudioTrack.write() → speaker
```

`AudioRecord` runs on a dedicated thread inside `PTTAudioManager`. Each 20ms chunk is passed via callback to `PTTRepository.sendAudioChunk()`, which calls `webSocket?.send(ByteString.of(*audioBytes))` — no coroutine, no queue, minimum latency path.

## Backend

`backend/server.py` is a Python `websockets` echo server used for local integration testing.

```bash
pip install websockets
python backend/server.py
# Listening on ws://0.0.0.0:8765
# Android emulator: ws://10.0.2.2:8765
# Real device (same WiFi): ws://<host-LAN-IP>:8765
```

On every frame — binary (audio) or text (JSON control) — the server prints the content and replies `"successfully received msg"`. Verified against all four message types: `INVITE`, `TEXT`, `BYE`, and binary audio chunks.

`PTTWebSocketService.WS_BASE_URL` sets the target. The `onMessage(text)` handler guards against non-JSON frames (the echo reply) with a try/catch on `Gson.fromJson` — a null result skips the Room insert silently.

## Testing Strategy

**17 tests total — 17 passing — 0 failing**

| Suite | Tests | Device | Status |
|-------|-------|--------|--------|
| JVM Unit Tests | 12 | Host JVM (JDK 17) | ✅ 12/12 |
| Espresso Instrumented | 5 | Moto G Power 5G — Android 15 | ✅ 5/5 |

### Unit Tests — 12/12 Passing

#### PTTViewModelTest (7 tests)
| Test | What it verifies |
|------|-----------------|
| `joining channel updates session state to Active` | `joinChannel()` drives `PTTSessionState` to `Active` |
| `sending empty message does not call repository` | Guard clause rejects blank content before repository call |
| `sending valid message calls repository with correct content` | Message content flows through to repository unchanged |
| `startPTT transitions through Connecting then Established` | `runCurrent()` → `Connecting`, `advanceUntilIdle()` → `Established` after 500ms delay |
| `stopPTT resets pttButtonState to Idle` | Cancels the pttJob, state resets regardless of prior state |
| `leaving channel sets uiState to Idle` | Full join → leave cycle resets UI state |
| `network failure on join shows Error state` | `FakePTTRepository.shouldThrowError` drives `PTTUiState.Error` |

All ViewModel tests use `FakePTTRepository` — no real database, no real WebSocket, no Android runtime. `StandardTestDispatcher` controls coroutine execution so timing-sensitive state transitions (`Connecting` vs `Established`) are asserted deterministically with `runCurrent()` and `advanceUntilIdle()`.

#### PTTRepositoryTest (5 tests)
| Test | What it verifies |
|------|-----------------|
| `joining channel sends INVITE message via WebSocket` | First sent message has `type = INVITE` and correct `channelId` |
| `joining channel transitions session state to Active` | Session state machine driven correctly by `joinChannel()` |
| `sending text message saves to Room before WebSocket` | Room insert happens; WebSocket also receives the message |
| `leaving channel sends BYE message` | Last sent message has `type = BYE` |
| `leaving channel transitions session state to Terminated` | Full lifecycle: `Idle → Active → Terminated` |

Repository tests use `FakeMessageDao`, `FakePTTWebSocketService`, and `FakeAudioManager` — pure JVM, 10x faster than instrumented tests.

### Instrumented Tests (Espresso) — 5/5 Passing on Device

Run on a Moto G Power 5G (Android 15) via wireless ADB.

| Test | What it verifies |
|------|-----------------|
| `channelList_isDisplayedOnLaunch` | All four channels render in the LazyColumn on cold start |
| `pttButton_isDisplayedAfterChannelSelection` | PTT button present after tapping a channel card |
| `leaveButton_isDisplayedAfterChannelSelection` | Leave button present on PTT screen |
| `networkStatusBar_isDisplayedAfterChannelSelection` | PTT screen renders after channel tap |
| `sendingMessage_appearsInMessageList` | Typed message survives Room round-trip and appears in list |

### Bugs Found and Fixed During Testing

**PTT button not releasing on finger lift**

Pressing the PTT button changed `pttButtonState` from `Idle → Connecting`. Because the modifier was `pointerInput(state)`, Compose restarted the entire gesture coroutine block on that state change — cancelling the in-flight `tryAwaitRelease()`. The 500ms later transition to `Established` cancelled it again. The release event was never observed, so `stopPTT()` was never called — the button stayed red indefinitely.

Fix: changed to `pointerInput(Unit)` (stable key, coroutine lives for the composable's lifetime) and wrapped state reads with `rememberUpdatedState` so the coroutine always reads the current value without restarting.

**Text messages sent but not appearing in the list**

`messages: StateFlow` was initialized as `repository.getMessages(currentChannelId)` where `currentChannelId` was `""` at construction. `stateIn` binds the upstream `Flow` once at creation — no matter which channel was later selected, the list always queried Room for `channelId = ""` and returned empty.

Fix: replaced with `_currentChannelId.flatMapLatest { id -> repository.getMessages(id) }`. `flatMapLatest` cancels and re-subscribes to the Room `Flow` whenever the channel changes, so the list always reflects the active channel.

**Espresso tests failing with "No compose hierarchies found"**

`PTTScreen` called `launchPermissionRequest()` inside a `LaunchedEffect(Unit)` at the top level — before the channel list `if` branch. On every cold start the mic permission dialog appeared, covering the `MainActivity` window. The Compose test framework couldn't find the hierarchy because the active foreground window was a native system dialog, not the app.

Fix: moved `LaunchedEffect` to after the `selectedChannel == null` guard — permission is now only requested after the user taps a channel. Added `GrantPermissionRule(RECORD_AUDIO)` to the test class so the dialog is auto-granted and never blocks subsequent tests that do trigger it.

**Tests intermittently failing on locked device**

Four of five Espresso tests failed with "No compose hierarchies found" on the first run — then passed on re-run with the device screen on. The test runner was launching the Activity behind the lock screen, where the Compose window was not the active foreground surface.

Fix: added `FLAG_SHOW_WHEN_LOCKED`, `FLAG_TURN_SCREEN_ON`, and `FLAG_KEEP_SCREEN_ON` window flags in `@Before` via `composeRule.activityRule.scenario.onActivity { }`, using the API 27+ `setShowWhenLocked()` / `setTurnScreenOn()` methods on newer devices.

## What I Learned

### Gesture Handling in Compose Has Layered Ownership

`Button(onClick)` is not a simple wrapper around `pointerInput`. It uses `Indication`, `Ripple`, and `clickable` internally — each of which participates in the pointer event dispatch chain. When `pointerInput(detectTapGestures)` is added to the same `Modifier` chain, both handlers see the same events. In practice, `clickable`'s gesture handler consumed the `onPress` event before `detectTapGestures` could establish a tracking context — `tryAwaitRelease()` never ran. Replacing `Button` with a `Box` and a manually applied `clip + background` removes the competing handler entirely. The lesson: composed Composables have implicit gesture semantics. For custom interaction, build from primitives.

### `pointerInput` Key Selection Is a Correctness Decision, Not a Performance One

The `key` parameter of `pointerInput` is commonly treated as an optimization hint. It is actually a coroutine lifetime control. Changing the key cancels and restarts the entire suspension block — including any `tryAwaitRelease()` that is currently suspended mid-gesture. State-driven keys (`pointerInput(state)`) are correct when you want to restart on state change (e.g., re-initializing gesture tracking logic). They are wrong when the state changes *during* an in-progress gesture, because the restart loses the gesture context. The correct pattern for dynamic behavior in a stable gesture block is `pointerInput(Unit)` + `rememberUpdatedState`.

### `flatMapLatest` Is the Correct Tool for Channel-Switching Flows

The naive pattern — `repository.getMessages(channelId).stateIn(...)` — evaluates `channelId` once at construction time. Updating a `var` or even a `MutableStateFlow` does nothing to change the bound upstream `Flow`. `flatMapLatest` solves this: it takes a `Flow<ChannelId>` as input and re-subscribes to a new `Flow<List<Message>>` every time the channel changes, cancelling the previous subscription. Without this, every channel selection queried the wrong Room slice and returned empty results — a bug invisible during manual testing if you always open the first channel.

### SharedFlow and StateFlow Are Not Interchangeable

`SharedFlow` and `StateFlow` differ on two axes that matter operationally: replay and initial value. `StateFlow` always has a current value and replays it to new subscribers — correct for connection status, session state, UI state. `SharedFlow` with `replay = 0` does not replay — correct for one-time events like incoming messages. A new subscriber to `SharedFlow` will not receive messages that arrived before they subscribed. Using `StateFlow` for messages would replay every historical message to every new collector (e.g., on screen rotation). Using `SharedFlow` for network state would leave a new subscriber without a current value until the next emission.

### SIP Protocol Concepts Apply Outside SIP

The INVITE/BYE pattern is not SIP-specific — it's a general-purpose session establishment pattern for stateful peer communication. Without explicit signaling, the server cannot distinguish "client is idle" from "client lost connectivity" from "client never joined." Sealed class state machines (`Idle → Inviting → Active → Terminating → Terminated`) enforce that every transition is explicit and traceable. There is no way to reach `Active` from `Terminating`, or to `sendAudioChunk` while `Idle` — the type system enforces the lifecycle.

### Permission Timing Affects Both UX and Testability

Requesting permissions at the earliest possible moment is not always correct. `RECORD_AUDIO` on cold start — before the user has any context for why the mic is needed — is a pattern that degrades both grant rates and test reliability. Deferring to the moment of actual use (channel join) gives the user context, increases grant likelihood, and eliminates the system dialog that was covering the Compose window during Espresso runs. The permission dialog is a native system surface — it is not part of the Compose hierarchy. Any Compose node lookup while the dialog is visible will fail with "no compose hierarchies found."

### Hilt + Compose Testing Requires Careful Rule Ordering

`createAndroidComposeRule<MainActivity>()` launches the Activity inside the rule's `before()` method, which runs as part of the JUnit rule chain — before `@Before` methods execute. If `hiltRule.inject()` is in `@Before`, the Hilt test component doesn't exist when `MainActivity.onCreate()` tries to inject the `ViewModel`. Rule ordering via `@get:Rule(order = N)` controls the wrap order: `hiltRule` at order 0 (outermost) initializes the Hilt component, then `permissionRule` at order 1 grants permissions, then `composeRule` at order 2 launches the Activity. Inside `@Before`, the Activity is already running — `composeRule.activityRule.scenario.onActivity { }` is safe.

### WebSocket Lifecycle Is a Memory Leak by Default

OkHttp's `newWebSocket()` returns immediately and connects in the background. The `OkHttpClient` holds a thread pool and connection pool internally. If the WebSocket is not explicitly closed (`webSocket.close(1000, ...)`) and the client is not shut down, both pools leak beyond the ViewModel's lifetime. The `disconnect()` method on `PTTWebSocketService` calls `close()` and nulls the reference — called from `PTTRepository.leaveChannel()`, which is called from `ViewModel.leaveChannel()`, which is triggered by both the Leave button and `onCleared()`.

## Project Structure

```
commlink/
├── backend/
│   └── server.py                  # Python WebSocket echo server
├── app/src/
│   ├── main/
│   │   └── java/com/commlink/app/
│   │       ├── data/
│   │       │   ├── audio/
│   │       │   │   └── PTTAudioManager.kt       # AudioRecord + AudioTrack
│   │       │   ├── local/
│   │       │   │   ├── dao/MessageDao.kt
│   │       │   │   ├── entity/MessageEntity.kt  # Room entity + mappers
│   │       │   │   └── CommLinkDatabase.kt
│   │       │   ├── repository/PTTRepository.kt  # Source of truth
│   │       │   └── websocket/PTTWebSocketService.kt
│   │       ├── di/
│   │       │   └── AppModule.kt                 # Hilt bindings
│   │       ├── domain/model/
│   │       │   ├── PTTMessage.kt + MessageType
│   │       │   ├── PTTSessionState.kt           # Sealed: Idle/Inviting/Active/Terminating/Terminated
│   │       │   ├── PTTButtonState.kt            # Sealed: Idle/Connecting/Established/Revoked
│   │       │   ├── NetworkState.kt              # Sealed: Connected/Disconnected/Reconnecting
│   │       │   ├── PTTChannel.kt
│   │       │   └── PTTUiState.kt
│   │       ├── ui/
│   │       │   ├── ptt/
│   │       │   │   ├── PTTScreen.kt             # Channel list + PTT screen
│   │       │   │   └── PTTViewModel.kt
│   │       │   ├── messaging/
│   │       │   ├── navigation/CommLinkNavigation.kt
│   │       │   └── theme/CommLinkTheme.kt
│   │       ├── CommLinkApplication.kt
│   │       └── MainActivity.kt
│   ├── test/
│   │   └── java/com/commlink/app/
│   │       ├── fake/                            # FakeRepository, FakeDao, FakeWebSocket, FakeAudio
│   │       ├── ui/ptt/PTTViewModelTest.kt       # 7 unit tests
│   │       └── data/repository/PTTRepositoryTest.kt  # 5 unit tests
│   └── androidTest/
│       └── java/com/commlink/app/
│           ├── HiltTestRunner.kt
│           └── ui/ptt/PTTScreenTest.kt          # 5 Espresso tests
└── build.gradle.kts
```
