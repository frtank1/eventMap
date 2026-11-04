package com.eventmap.event.ui.viewmodel

import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.core.network.models.ErrorModel
import com.eventmap.event.models.Ticket
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlin.test.*

class EventDetailFailureTest {
    @BeforeTest fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }
    @Test fun readFailureDoesNotInventEventAndCanRetry() = runTest {
        val repo = FakeEventRepository()
        val real = repo.eventResponse
        repo.eventResponse = KtorResponse.Failure(ErrorModel(customMessage = "offline"))
        val vm = EventDetailViewModel(repo, dispatcher = StandardTestDispatcher(testScheduler))
        vm.load("event-1")
        advanceUntilIdle()
        assertNull(vm.event.value)
        assertTrue(vm.participants.value.isEmpty())
        assertTrue(vm.ratings.value.isEmpty())
        assertEquals("offline", vm.errorText.value)
        repo.eventResponse = real
        vm.load("event-1")
        advanceUntilIdle()
        assertEquals("event-1", vm.event.value?.id)
    }

    @Test fun rejectedWritesDoNotBecomePaidAttendedOrRated() = runTest {
        val vm = EventDetailViewModel(FakeEventRepository(), dispatcher = StandardTestDispatcher(testScheduler))
        vm.load("event-1")
        advanceUntilIdle()
        vm.purchase()
        vm.attend()
        vm.submitRating(5, "Review")
        advanceUntilIdle()
        assertEquals("none", vm.ticketStatus.value)
        assertFalse(vm.attended.value)
        assertFalse(vm.ratingSent.value)
        assertTrue(vm.ratings.value.isEmpty())
    }

    @Test fun pendingPaymentRemainsPending() = runTest {
        val repo = FakeEventRepository().apply { purchaseResponse = KtorResponse.Success(Ticket("pending")) }
        val vm = EventDetailViewModel(repo, dispatcher = StandardTestDispatcher(testScheduler))
        vm.load("event-1")
        advanceUntilIdle()
        vm.purchase()
        advanceUntilIdle()
        assertEquals("pending", vm.ticketStatus.value)
    }

    @Test fun emptyPaymentStatusIsNotTreatedAsPaid() = runTest {
        val repo = FakeEventRepository().apply { purchaseResponse = KtorResponse.Success(Ticket("")) }
        val vm = EventDetailViewModel(repo, dispatcher = StandardTestDispatcher(testScheduler))
        vm.load("event-1")
        advanceUntilIdle()
        vm.purchase()
        advanceUntilIdle()
        assertEquals("none", vm.ticketStatus.value)
    }
}
