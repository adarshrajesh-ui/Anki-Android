// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — Exam configuration editor (Increment 4).
//
// Sets the CFA exam date and persists it under `cfa_exam_config` in col.conf,
// which syncs natively (no new sync endpoint — this is data, not sync plumbing).
// The scores screen and the exam-priority queue both read this key.

package com.ichi2.anki

import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.cfa.CfaExamConfig
import com.ichi2.anki.snackbar.showSnackbar
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class CfaExamConfigActivity : AnkiActivity(R.layout.activity_cfa_exam_config) {
    /** Currently-selected exam date (ISO yyyy-MM-dd), or null when unset. */
    private var selectedDate: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        val toolbar = findViewById<Toolbar>(R.id.cfa_config_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.cfa_exam_config_title)
        }

        val dateView = findViewById<TextView>(R.id.cfa_config_date_value)

        launchCatchingTask {
            val existing = withCol { CfaExamConfig.read(this) }
            selectedDate = existing?.examDate
            dateView.text = selectedDate ?: getString(R.string.cfa_exam_config_no_date)
        }

        findViewById<MaterialButton>(R.id.cfa_config_pick_date).setOnClickListener {
            showDatePicker(dateView)
        }
        findViewById<MaterialButton>(R.id.cfa_config_save).setOnClickListener {
            save()
        }
    }

    private fun showDatePicker(dateView: TextView) {
        val selection =
            selectedDate?.let {
                // Fully-qualified to avoid AnkiActivity's suspend `runCatching` extension.
                kotlin
                    .runCatching {
                        LocalDate
                            .parse(it)
                            .atStartOfDay(ZoneOffset.UTC)
                            .toInstant()
                            .toEpochMilli()
                    }.getOrNull()
            } ?: MaterialDatePicker.todayInUtcMilliseconds()

        val picker =
            MaterialDatePicker.Builder
                .datePicker()
                .setTitleText(R.string.cfa_exam_config_date_label)
                .setSelection(selection)
                .build()
        picker.addOnPositiveButtonClickListener { millis ->
            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            selectedDate = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            dateView.text = selectedDate
        }
        picker.show(supportFragmentManager, "cfa_exam_date")
    }

    private fun save() {
        val date = selectedDate
        if (date == null) {
            showSnackbar(R.string.cfa_exam_config_no_date)
            return
        }
        launchCatchingTask {
            withCol { CfaExamConfig.writeExamDate(this, date) }
            Timber.i("CFA exam date saved")
            showSnackbar(R.string.cfa_exam_config_saved)
            finish()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
