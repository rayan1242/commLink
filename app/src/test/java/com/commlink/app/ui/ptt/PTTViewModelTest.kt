package com.commlink.app.ui.ptt

import com.commlink.app.domain.model.PTTButtonState
import com.commlink.app.domain.model.PTTSessionState
import com.commlink.app.domain.model.PTTUiState
import com.commlink.app.fake.FakePTTRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PTTViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakePTTRepository
    private lateinit var viewModel: PTTViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakePTTRepository()
        viewModel = PTTViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `joining channel updates session state to Active`() = runTest {
        viewModel.joinChannel("channel_alpha")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeRepository.sessionState.value is PTTSessionState.Active)
    }

    @Test
    fun `sending empty message does not call repository`() = runTest {
        viewModel.sendTextMessage("")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeRepository.sentMessages.isEmpty())
    }

    @Test
    fun `sending valid message calls repository with correct content`() = runTest {
        viewModel.sendTextMessage("All units respond")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeRepository.sentMessages.isNotEmpty())
        assertEquals("All units respond", fakeRepository.sentMessages.first().content)
    }

    @Test
    fun `startPTT transitions pttButtonState through Connecting then Established`() = runTest {
        viewModel.startPTT()
        testDispatcher.scheduler.runCurrent()   // run up to the delay — state = Connecting
        assertEquals(PTTButtonState.Connecting, viewModel.pttButtonState.value)

        testDispatcher.scheduler.advanceUntilIdle()  // advance past 500ms — state = Established
        assertEquals(PTTButtonState.Established, viewModel.pttButtonState.value)
    }

    @Test
    fun `stopPTT resets pttButtonState to Idle`() = runTest {
        viewModel.startPTT()
        testDispatcher.scheduler.runCurrent()
        viewModel.stopPTT()

        assertEquals(PTTButtonState.Idle, viewModel.pttButtonState.value)
    }

    @Test
    fun `leaving channel sets uiState to Idle`() = runTest {
        viewModel.joinChannel("channel_alpha")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.leaveChannel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is PTTUiState.Idle)
    }

    @Test
    fun `network failure on join shows Error state`() = runTest {
        fakeRepository.shouldThrowError = true

        viewModel.joinChannel("channel_alpha")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is PTTUiState.Error)
    }
}
