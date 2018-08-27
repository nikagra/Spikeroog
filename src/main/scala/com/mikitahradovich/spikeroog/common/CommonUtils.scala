package com.mikitahradovich.spikeroog.common

import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter

object CommonUtils {
  def millisToFormattedDate(millis: Long, format: String = "dd/MM/yyyy") =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate.format(DateTimeFormatter.ofPattern(format))
}
