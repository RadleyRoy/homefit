package com.radley.homefit.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import com.radley.homefit.data.HistoryRepository
import com.radley.homefit.data.SettingsStore
import com.radley.homefit.data.content.WorkoutCategory
import com.radley.homefit.data.db.SessionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first

/**
 * Health Connect is the Android-wide replacement for Google Fit sync. Every call here is
 * best-effort: a missing provider or a revoked permission must never break finishing a workout,
 * so failures are swallowed and the in-app record stays the source of truth.
 */
class HealthConnectSync(
    private val context: Context,
    private val history: HistoryRepository,
    private val settings: SettingsStore,
) {

    enum class Status { UNAVAILABLE, NEEDS_INSTALL, NEEDS_PERMISSION, READY }

    private fun client(): HealthConnectClient? =
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
        } else null

    suspend fun status(): Status = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_UNAVAILABLE -> Status.UNAVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Status.NEEDS_INSTALL
        else -> {
            val granted = runCatching { client()?.permissionController?.getGrantedPermissions() }.getOrNull().orEmpty()
            if (granted.containsAll(PERMISSIONS)) Status.READY else Status.NEEDS_PERMISSION
        }
    }

    private suspend fun enabledClient(): HealthConnectClient? {
        if (!settings.currentAppPrefs().healthConnectSync) return null
        return if (status() == Status.READY) client() else null
    }

    suspend fun writeSession(session: SessionEntity) {
        val client = enabledClient() ?: return
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(session.startedAt)
        val end = Instant.ofEpochMilli(session.endedAt).coerceAtLeast(start.plusSeconds(1))
        val offset = zone.rules.getOffset(start)
        val type = when (runCatching { WorkoutCategory.valueOf(session.category) }.getOrNull()) {
            WorkoutCategory.STRETCH, WorkoutCategory.WARMUP -> ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING
            WorkoutCategory.HIIT -> ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
            WorkoutCategory.DUMBBELL -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
            WorkoutCategory.RUNNING -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
            else -> ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS
        }
        runCatching {
            client.insertRecords(
                listOf<Record>(
                    ExerciseSessionRecord(
                        startTime = start,
                        startZoneOffset = offset,
                        endTime = end,
                        endZoneOffset = offset,
                        metadata = Metadata.activelyRecorded(PHONE, clientRecordId = "session-${session.id}", clientRecordVersion = 1),
                        exerciseType = type,
                        title = session.title,
                    ),
                    ActiveCaloriesBurnedRecord(
                        startTime = start,
                        startZoneOffset = offset,
                        endTime = end,
                        endZoneOffset = offset,
                        energy = Energy.kilocalories(session.kcal),
                        metadata = Metadata.activelyRecorded(PHONE, clientRecordId = "kcal-${session.id}", clientRecordVersion = 1),
                    ),
                ),
            )
        }
    }

    suspend fun writeWeight(epochDay: Long, kg: Double) {
        val client = enabledClient() ?: return
        val time = LocalDate.ofEpochDay(epochDay).atTime(8, 0).atZone(ZoneId.systemDefault())
        runCatching {
            client.insertRecords(
                listOf(
                    WeightRecord(
                        time = time.toInstant(),
                        zoneOffset = time.offset,
                        weight = Mass.kilograms(kg),
                        metadata = Metadata.manualEntry(clientRecordId = "weight-$epochDay", clientRecordVersion = System.currentTimeMillis()),
                    ),
                ),
            )
        }
    }

    /** Copies the last 90 days of weigh-ins from other apps, keeping the latest per day. Returns how many were new. */
    suspend fun importWeights(): Int {
        val client = client() ?: error("Health Connect isn't available")
        val since = Instant.now().minus(90, ChronoUnit.DAYS)
        val records = client.readRecords(ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.after(since))).records
            .filter { it.metadata.dataOrigin.packageName != context.packageName }
        val existing = history.weights.first().map { it.epochDay }.toSet()
        val byDay = records.groupBy { it.time.atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() }
            .mapValues { (_, list) -> list.maxBy { it.time }.weight.inKilograms }
        var added = 0
        for ((day, kg) in byDay) {
            if (day !in existing) added++
            // Not logWeight: that would echo the value straight back to Health Connect.
            history.storeWeight(day, kg)
        }
        return added
    }

    fun openInstall() {
        val uri = Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding")
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    companion object {
        private const val PROVIDER = "com.google.android.apps.healthdata"
        private val PHONE = Device(type = Device.TYPE_PHONE)

        val PERMISSIONS = setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getWritePermission(WeightRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
        )

        fun permissionContract() = PermissionController.createRequestPermissionResultContract()
    }
}
