package com.eventmap.event.ui.viewmodel

import com.eventmap.core.datastore.EventDraftStorage
import com.eventmap.core.datastore.SecureStorage
import com.eventmap.core.network.ktor.KtorResponse
import com.eventmap.event.models.EventDetail
import com.eventmap.event.models.EventLocation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlin.test.*

class PublicationTest {
    @BeforeTest fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }
    private fun TestScope.model(repo: FakeEventRepository, drafts: EventDraftStorage? = null) =
        CreateEventViewModel(repo, drafts, StandardTestDispatcher(testScheduler)).also {
            it.update { CreateForm(title = "Football", category = "Спорт", locationName = "Field",
                lat = 51.1, lng = 71.4, locationSelected = true,
                date = "2030-06-01", time = "20:00", timeZoneId = "Asia/Almaty") }
        }

    private fun video() = PickedMedia(byteArrayOf(1), "video/mp4", "video.mp4")

    @Test fun missingMapSelectionDoesNotPublishRandomCoordinates() = runTest {
        val repo = FakeEventRepository()
        val vm = model(repo)
        vm.update { it.copy(locationSelected = false) }
        vm.publish()
        advanceUntilIdle()
        assertEquals(0, repo.creates)
        assertNotNull(vm.errorText.value)
    }

    @Test fun convertsLocalTimeAndPreservesSelectedCoordinates() = runTest {
        val repo = FakeEventRepository()
        val vm = model(repo)
        vm.publish()
        advanceUntilIdle()
        assertEquals("2030-06-01T15:00:00Z", repo.draft?.startAt)
        assertEquals(51.1, repo.draft?.location?.lat)
        assertEquals(71.4, repo.draft?.location?.lng)
        assertTrue(vm.created.value)
    }

    @Test fun doubleTapSendsOnlyOneCreate() = runTest {
        val repo = FakeEventRepository()
        val vm = model(repo)
        vm.publish()
        vm.publish()
        advanceUntilIdle()
        assertEquals(1, repo.creates)
    }

    @Test fun coverFailureStopsBeforeCreatingEvent() = runTest {
        val repo = FakeEventRepository().apply { failUploadKind = "image" }
        val vm = model(repo)
        vm.setCover(PickedMedia(byteArrayOf(1), "image/jpeg", "cover.jpg"))
        vm.publish()
        advanceUntilIdle()
        assertEquals(0, repo.creates)
        assertFalse(vm.created.value)
        assertFalse(vm.loading.value)
        assertNotNull(vm.errorText.value)
        assertEquals("Football", vm.form.value.title)
    }

    @Test fun videoFailureResumesWithoutCreatingAnotherEvent() = runTest {
        val repo = FakeEventRepository().apply { failUploadKind = "video" }
        val vm = model(repo)
        vm.setReel(video())
        vm.publish()
        advanceUntilIdle()
        assertFalse(vm.created.value)
        assertEquals("event-1", vm.form.value.pendingEventId)
        repo.failUploadKind = null
        vm.publish()
        advanceUntilIdle()
        assertTrue(vm.created.value)
        assertEquals(1, repo.creates)
        assertEquals(1, repo.registrations)
    }

    @Test fun registrationRetryDoesNotUploadAgain() = runTest {
        val repo = FakeEventRepository().apply { failRegistration = true }
        val vm = model(repo)
        vm.setReel(video())
        vm.publish()
        advanceUntilIdle()
        assertFalse(vm.created.value)
        assertNotNull(vm.form.value.pendingReelUrl)
        repo.failRegistration = false
        vm.publish()
        advanceUntilIdle()
        assertTrue(vm.created.value)
        assertEquals(1, repo.creates)
        assertEquals(1, repo.uploads)
        assertEquals(2, repo.registrations)
    }

    @Test fun processingMuxVideoKeepsMediaIdForRetryWithoutSignedUrlInDraft() = runTest {
        val repo = FakeEventRepository().apply {
            failRegistration = true
            uploadedMedia = com.eventmap.event.models.UploadedMedia("mux-test", "", "processing")
        }
        val vm = model(repo)
        vm.setReel(video())
        vm.publish()
        advanceUntilIdle()
        assertEquals("", vm.form.value.pendingReelUrl)
        assertEquals("mux-test", vm.form.value.pendingReelMediaId)
        assertEquals("mux-test", repo.registeredMediaId)
        repo.failRegistration = false
        vm.publish()
        advanceUntilIdle()
        assertTrue(vm.created.value)
        assertEquals(1, repo.uploads)
        assertEquals(1, repo.creates)
    }

    @Test fun failedCreateRetainsDraftAndDoesNotUploadReel() = runTest {
        val repo = FakeEventRepository().apply { createFailure = true }
        val vm = model(repo)
        vm.setReel(video())
        vm.publish()
        advanceUntilIdle()
        assertFalse(vm.created.value)
        assertEquals(0, repo.uploads)
        assertEquals("Football", vm.form.value.title)
    }

    @Test fun validatesMalformedTimeAndCoordinates() = runTest {
        for (transform in listOf<(CreateForm) -> CreateForm>(
            { it.copy(time = "25:99") }, { it.copy(lat = Double.NaN) },
            { it.copy(lng = 181.0) }, { it.copy(limit = "one") },
        )) {
            val repo = FakeEventRepository()
            val vm = model(repo)
            vm.update(transform)
            vm.publish()
            advanceUntilIdle()
            assertEquals(0, repo.creates)
        }
    }

    @Test fun editingPreservesCapacityAndConvertsDateBackToUtc() = runTest {
        val repo = FakeEventRepository().apply {
            eventResponse = KtorResponse.Success(EventDetail("edit-id", "Football", category = "Спорт",
                startAt = "2030-06-01T15:00:00Z", endAt = "2030-06-01T17:00:00Z",
                location = EventLocation(51.1, 71.4, "Field"), capacity = 10))
        }
        val vm = model(repo)
        vm.loadForEdit("edit-id")
        advanceUntilIdle()
        assertEquals("10", vm.form.value.limit)
        vm.publish()
        advanceUntilIdle()
        assertEquals(1, repo.updates)
        assertEquals(10, repo.draft?.limit)
        assertEquals("2030-06-01T15:00:00Z", repo.draft?.startAt)
        assertEquals("2030-06-01T17:00:00Z", repo.draft?.endAt)
    }

    @Test fun confirmedCreateResumesAfterViewModelRecreation() = runTest {
        val values = mutableMapOf<String, String>()
        val storage = object : SecureStorage {
            override fun getString(key: String) = values[key]
            override fun putString(key: String, value: String) { values[key] = value }
            override fun remove(key: String) { values.remove(key) }
        }
        val drafts = EventDraftStorage(storage) { "alice" }
        val repo = FakeEventRepository().apply { failRegistration = true }
        val vm = model(repo, drafts)
        vm.setReel(video())
        vm.publish()
        advanceUntilIdle()
        assertNotNull(drafts.load())
        val restored = CreateEventViewModel(repo, drafts, StandardTestDispatcher(testScheduler))
        assertEquals("event-1", restored.form.value.pendingEventId)
        repo.failRegistration = false
        restored.publish()
        advanceUntilIdle()
        assertTrue(restored.created.value)
        assertEquals(1, repo.creates)
        assertEquals(1, repo.uploads)
        assertNull(drafts.load())
    }
}
