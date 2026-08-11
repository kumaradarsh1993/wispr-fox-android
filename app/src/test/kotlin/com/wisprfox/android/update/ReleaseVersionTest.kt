package com.wisprfox.android.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {
    @Test fun nightlySequenceIsCompared() {
        assertTrue(isReleaseNewer("v2.2.0-nightly.2", "v2.2.0-nightly.1"))
        assertFalse(isReleaseNewer("v2.2.0-nightly.1", "v2.2.0-nightly.2"))
        assertFalse(isReleaseNewer("v2.2.0-nightly.2", "v2.2.0-nightly.2"))
    }

    @Test fun stableWinsOverSameBaseNightly() {
        assertTrue(isReleaseNewer("v2.2.0", "v2.2.0-nightly.2"))
        assertFalse(isReleaseNewer("v2.2.0-nightly.2", "v2.2.0"))
    }

    @Test fun baseVersionStillTakesPriority() {
        assertTrue(isReleaseNewer("v2.3.0-nightly.1", "v2.2.9"))
        assertFalse(isReleaseNewer("v2.2.9", "v2.3.0-nightly.1"))
    }
}
