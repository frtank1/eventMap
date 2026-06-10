package com.eventmap.map.screen

import com.eventmap.event.models.EventDetail
import com.eventmap.event.models.EventLocation
import com.eventmap.event.models.EventPrice
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapViewModelTest {

    private fun event(id: String, priceType: String = "free", status: String = "live") = EventDetail(
        id = id,
        title = "Событие $id",
        category = "Концерт",
        status = status,
        location = EventLocation(51.1, 71.4, "Астана"),
        price = EventPrice(priceType, if (priceType == "paid") 2000 else 0),
    )

    @Test
    fun prodMode_backendDataShownOnMap() = runTest {
        val vm = MapViewModel(
            eventRepository = FakeEventRepository.success(listOf(event("1"), event("2"))),
            demoMode = false,
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        advanceUntilIdle()

        assertEquals(listOf("1", "2"), vm.events.value.map { it.id })
    }

    @Test
    fun prodMode_networkFailureEmitsError_mapEmpty() = runTest {
        val vm = MapViewModel(
            eventRepository = FakeEventRepository.failure("network error"),
            demoMode = false,
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        advanceUntilIdle()
        assertTrue(vm.events.value.isEmpty())

        val errors = mutableListOf<String>()
        val job = launch { vm.errorMessage.collect { errors += it } }
        vm.reload()
        advanceUntilIdle()

        assertEquals(listOf("network error"), errors)
        job.cancel()
    }

    @Test
    fun demoMode_networkFailureKeepsSamples_noError() = runTest {
        val vm = MapViewModel(
            eventRepository = FakeEventRepository.failure(),
            demoMode = true,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val errors = mutableListOf<String>()
        val job = launch { vm.errorMessage.collect { errors += it } }
        advanceUntilIdle()

        assertTrue(vm.events.value.isNotEmpty()) // sampleEvents
        assertTrue(errors.isEmpty())
        job.cancel()
    }

    @Test
    fun freeOnlyFilter_keepsOnlyFreeEvents() = runTest {
        val vm = MapViewModel(
            eventRepository = FakeEventRepository.success(
                listOf(event("free-1", "free"), event("paid-1", "paid")),
            ),
            demoMode = false,
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        advanceUntilIdle()
        assertEquals(2, vm.events.value.size)

        vm.setFreeOnly(true)

        assertEquals(listOf("free-1"), vm.events.value.map { it.id })

        vm.resetFilter()
        assertEquals(2, vm.events.value.size)
    }
}
