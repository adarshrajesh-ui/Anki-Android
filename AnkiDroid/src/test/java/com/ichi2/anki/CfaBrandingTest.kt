// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — Increment 1 (branding + nav) regression tests.
//
// Guards the two additive resource changes that make the AnkiDroid fork present
// as "ankiCFA": the app label and the flagship Exam Readiness nav-drawer entry.
// Both fail without the Increment 1 change and pass with it.

package com.ichi2.anki

import android.content.Context
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.widget.PopupMenu
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CfaBrandingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `app is branded ankiCFA`() {
        // Set via build.gradle resValue; the CFA identity of the fork.
        assertThat(context.getString(R.string.app_name), equalTo("ankiCFA"))
    }

    @Test
    fun `exam readiness is the first navigation drawer entry`() {
        val menu: Menu = PopupMenu(context, View(context)).menu
        MenuInflater(context).inflate(R.menu.navigation_drawer, menu)

        val first = menu.getItem(0)
        assertThat("CFA Exam Readiness must be the first nav item", first.itemId, equalTo(R.id.nav_cfa_readiness))
        assertThat(first.title.toString(), equalTo(context.getString(R.string.cfa_nav_exam_readiness)))
    }
}
