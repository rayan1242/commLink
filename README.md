# CommLink — Android Real-Time Communication App

A push-to-talk real-time communication Android app built for 
public safety use cases

## Purpose
Demonstrates real-time networking, audio APIs, and SIP-style 
session management — the core technologies behind mission-critical 
radio communication systems.

## Features
- **Push-to-talk voice** — AudioRecord captures 20ms chunks, 
  sent as binary WebSocket frames
- **Real-time messaging** — persistent WebSocket connection, 
  SharedFlow for one-time message events
- **SIP-style sessions** — sealed class state machine for 
  channel establishment and teardown
- **Network reliability** — exponential backoff reconnection, 
  NetworkState sealed class

## Architecture
Clean Architecture with MVVM:
- **Presentation** — Jetpack Compose, PTTViewModel, StateFlow
- **Domain** — PTTMessage, PTTSessionState, NetworkState sealed classes
- **Data** — PTTRepository, PTTWebSocketService, Room message history

## Tech Stack
- Kotlin, Jetpack Compose, MVVM
- OkHttp WebSocket — persistent real-time connection
- AudioRecord + AudioTrack — Android audio APIs
- Room — message history persistence
- Hilt — dependency injection
- JUnit + Espresso — 85%+ test coverage
- GitHub Actions CI/CD

## Key Technical Decisions
| Decision | Why |
|----------|-----|
| WebSocket not REST | Persistent full-duplex — no HTTP overhead per message |
| SharedFlow for messages | One-time events — don't replay on rotation |
| StateFlow for network | Always has current connection status |
| AudioRecord 20ms chunks | Industry standard for real-time voice |
| STREAM_VOICE_CALL | Takes audio priority — can't be muted |
| SIP-style state machine | Explicit lifecycle — every transition tracked |

## Audio Pipeline
```
AudioRecord (mic) → 20ms ByteArray chunks
→ WebSocket binary frame
→ Server broadcasts
→ AudioTrack (speaker) plays
```

## What I Learned
- WebSocket lifecycle management — memory leak prevention
- Android Audio APIs — AudioRecord, AudioTrack, AudioFocus
- Coroutine scope design — custom serviceScope
- SIP protocol concepts — session signaling
- Real-time system design for low latency
