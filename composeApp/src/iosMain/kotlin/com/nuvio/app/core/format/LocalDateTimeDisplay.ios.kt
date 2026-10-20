package com.nuvio.app.core.format

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.dateWithTimeIntervalSince1970

internal actual fun formatLocalDateTimeWithPatterns(
    epochMs: Long,
    localeTag: String,
    datePattern: String?,
    timePattern: String,
): String {
    val date = NSDate.dateWithTimeIntervalSince1970(epochMs / 1_000.0)
    val locale = NSLocale(localeIdentifier = localeTag)
    val dateText = NSDateFormatter().apply {
        this.locale = locale
        if (datePattern != null) {
            dateFormat = datePattern
        } else {
            dateStyle = NSDateFormatterShortStyle
            timeStyle = NSDateFormatterNoStyle
        }
    }.stringFromDate(date)
    val timeText = NSDateFormatter().apply {
        this.locale = locale
        dateFormat = timePattern
    }.stringFromDate(date)
    return "$dateText $timeText"
}

internal actual fun formatLocalTimeWithPattern(
    epochMs: Long,
    localeTag: String,
    timePattern: String,
): String = NSDateFormatter().apply {
    locale = NSLocale(localeIdentifier = localeTag)
    dateFormat = timePattern
}.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMs / 1_000.0))
