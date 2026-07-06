// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — Home / Concept Map reload-on-resume tests. Mirrors the reference
// CfaExamReadinessActivity behaviour: a sync or study session completed while the
// screen was backgrounded must show on return without an app restart. The first
// resume is intentionally skipped (onCreate already loaded the payload) so first
// open never double-loads.

package com.ichi2.anki.cfa

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CfaConceptMapActivity
import com.ichi2.anki.CfaHomeActivity
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController

@RunWith(AndroidJUnit4::class)
class CfaResumeReloadTest : RobolectricTest() {
    @Test
    fun `home loads once on first open then reloads on every return`() {
        val controller = launch(CfaHomeActivity::class.java)
        val activity = controller.get()

        // onCreate loaded the payload; the first resume is deliberately skipped.
        assertThat("loaded once on first open, not double-loaded", activity.payloadLoadCount, equalTo(1))

        returnToScreen(controller)
        assertThat("reloaded on return", activity.payloadLoadCount, equalTo(2))

        returnToScreen(controller)
        assertThat("reloaded again on next return", activity.payloadLoadCount, equalTo(3))
    }

    @Test
    fun `concept map loads once on first open then reloads on return`() {
        val controller = launch(CfaConceptMapActivity::class.java)
        val activity = controller.get()

        assertThat("loaded once on first open, not double-loaded", activity.payloadLoadCount, equalTo(1))

        returnToScreen(controller)
        assertThat("reloaded on return", activity.payloadLoadCount, equalTo(2))
    }

    private fun <T : android.app.Activity> launch(clazz: Class<T>): ActivityController<T> {
        val controller =
            Robolectric
                .buildActivity(clazz)
                .create()
                .start()
                .resume()
                .visible()
        saveControllerForCleanup(controller)
        advanceRobolectricLooper()
        return controller
    }

    /** A genuine background -> foreground round-trip (onPause then onResume). */
    private fun returnToScreen(controller: ActivityController<*>) {
        controller.pause().resume()
        advanceRobolectricLooper()
    }
}
