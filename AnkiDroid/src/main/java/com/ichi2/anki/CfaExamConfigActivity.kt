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
        val countdownView = findViewById<TextView>(R.id.cfa_config_countdown)

        launchCatchingTask {
            val existing = withCol { CfaExamConfig.read(this) }
            selectedDate = existing?.examDate
            dateView.text = selectedDate ?: getString(R.string.cfa_exam_config_no_date)
            renderCountdown(countdownView)
        }

        findViewById<MaterialButton>(R.id.cfa_config_pick_date).setOnClickListener {
            showDatePicker(dateView, countdownView)
        }
        findViewById<MaterialButton>(R.id.cfa_config_save).setOnClickListener {
            save()
        }
    }

    /**
     * Show a live "N days to the exam" preview under the date when one is set,
     * so the screen isn't a bare title + field (Phase B Pass-2 M4-2). Purely a
     * presentation of the already-selected date via the testable
     * [CfaExamConfig.daysUntil]; nothing is persisted here.
     */
    private fun renderCountdown(countdownView: TextView) {
        val days = CfaExamConfig.daysUntil(selectedDate, LocalDate.now(ZoneOffset.UTC))
        if (days == null) {
            countdownView.visibility = android.view.View.GONE
            return
        }
        countdownView.text =
            when {
                days > 0L ->
                    resources.getQuantityString(
                        R.plurals.cfa_exam_config_countdown,
                        days.toInt(),
                        days.toInt(),
                    )
                days == 0L -> getString(R.string.cfa_exam_config_countdown_today)
                else -> getString(R.string.cfa_exam_config_countdown_past)
            }
        countdownView.visibility = android.view.View.VISIBLE
    }

    private fun showDatePicker(
        dateView: TextView,
        countdownView: TextView,
    ) {
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
            renderCountdown(countdownView)
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
