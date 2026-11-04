package com.eventmap.event.ui.viewmodel

import com.eventmap.event.models.UploadedMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AddMediaTest {
    @BeforeTest fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    @Test fun retryPublishesSameProcessingMediaWithoutSecondUpload() = runTest {
        val repo = FakeEventRepository().apply {
            uploadedMedia = UploadedMedia("mux-test", "", "processing")
            failRegistration = true
        }
        val vm = AddMediaViewModel(repo)
        vm.choose(PickedMedia(byteArrayOf(1), "video/mp4", "v.mp4"))
        vm.publish("event-1")
        advanceUntilIdle()
        assertNull(vm.state.value.done)
        repo.failRegistration = false
        vm.publish("event-1")
        vm.publish("event-1")
        advanceUntilIdle()
        assertEquals(1, repo.uploads)
        assertEquals(2, repo.registrations)
        assertEquals("mux-test", repo.registeredMediaId)
        assertTrue(vm.state.value.done!!.contains("обработки"))
        assertFalse(vm.state.value.loading)
    }

    @Test fun photoDoesNotPublishAReel() = runTest {
        val repo = FakeEventRepository()
        val vm = AddMediaViewModel(repo)
        vm.choose(PickedMedia(byteArrayOf(1), "image/jpeg", "p.jpg"))
        vm.publish("event-1")
        advanceUntilIdle()
        assertEquals(1, repo.uploads)
        assertEquals(0, repo.registrations)
        assertNotNull(vm.state.value.done)
    }
}
