package de.mimuc.senseeverything.db.models

import androidx.room.DatabaseView

/**
 * flagsSatisfied is true when either: no flags are defined for the sensorName or ALL flags are present for the row
 */
@DatabaseView(
    viewName = "log_data_flag_status", value = """
    SELECT l.id as logDataId, (NOT EXISTS (
    SELECT 1 FROM sensor_upload_requirement r
    WHERE r.sensorName = l.sensorName
      AND NOT EXISTS (
        SELECT 1 FROM LogDataFlag f WHERE f.logDataId = l.id AND f.flag = r.flag))) as flagsSatisfied FROM logdata l
"""
)
data class LogDataFlagStatus(
    val logDataId: Int,
    val flagsSatisfied: Boolean
)
