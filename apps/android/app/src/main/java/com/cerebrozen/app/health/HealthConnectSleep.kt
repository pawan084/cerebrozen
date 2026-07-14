package com.cerebrozen.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZoneId

/**
 * Optional last-night sleep prefill from Health Connect — the Android analogue of
 * the iOS HealthKit sleep import. Everything degrades cleanly: if Health Connect
 * isn't installed, permission isn't granted, or there's no recent session, the
 * caller just keeps entering times by hand. Read-only; sleep sessions only.
 */
object HealthConnectSleep {
    val permissions = setOf(HealthPermission.getReadPermission(SleepSessionRecord::class))

    /** True when Health Connect is installed and usable on this device. */
    fun available(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private fun client(context: Context): HealthConnectClient? =
        if (available(context)) runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() else null

    /** Whether our sleep-read permission is already granted. */
    suspend fun hasPermission(context: Context): Boolean {
        val client = client(context) ?: return false
        return runCatching {
            client.permissionController.getGrantedPermissions().containsAll(permissions)
        }.getOrDefault(false)
    }

    /** The most recent sleep session in the last ~36h → (bed minute-of-day, wake
     * minute-of-day), or null if unavailable / no data. */
    suspend fun readLastNight(context: Context): Pair<Int, Int>? {
        val client = client(context) ?: return null
        val end = Instant.now()
        val start = end.minusSeconds(36 * 3600)
        val response = runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            )
        }.getOrNull() ?: return null
        val session = response.records.maxByOrNull { it.endTime } ?: return null
        val zone = ZoneId.systemDefault()
        val bed = session.startTime.atZone(zone).toLocalTime()
        val wake = session.endTime.atZone(zone).toLocalTime()
        return (bed.hour * 60 + bed.minute) to (wake.hour * 60 + wake.minute)
    }
}
