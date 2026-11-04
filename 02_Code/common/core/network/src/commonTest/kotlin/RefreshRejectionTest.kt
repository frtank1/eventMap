import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RefreshRejectionTest {
    @Test fun onlyDefinitiveRejectionTerminatesSession() {
        assertTrue(refreshRejectsSession(401))
        assertTrue(refreshRejectsSession(403))
        listOf(200, 400, 408, 429, 500, 502, 503, 504).forEach {
            assertFalse(refreshRejectsSession(it), "HTTP $it must not erase the session")
        }
    }
}
