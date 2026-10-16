package com.gpsstamp.camera.ultrawide

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.resume

class PendingCallsTest {
    @Test fun await_returnsWhenTheCallbackResumes() = runBlocking {
        val calls = PendingCalls()
        assertEquals(5, calls.await<Int> { it.resume(5) })
    }

    @Test fun close_failsAWaitThatWouldOtherwiseHangForever() = runBlocking {
        val calls = PendingCalls()
        // Like a still capture whose ImageReader was closed: the callback never comes.
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { runCatching { calls.await<Int> { } } }
        yield()
        calls.close()
        assertTrue(waiting.await().exceptionOrNull() is UltraWideUnavailable)
    }

    @Test fun await_afterClose_failsImmediately() = runBlocking {
        val calls = PendingCalls()
        calls.close()
        val result = runCatching { calls.await<Int> { it.resume(1) } }
        assertTrue(result.exceptionOrNull() is UltraWideUnavailable)
    }
}
