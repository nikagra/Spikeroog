package com.mikitahradovich.spikeroog.common

import java.awt.Color
import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, ZoneId}

import org.javacord.api.entity.message.embed.EmbedBuilder
import org.javacord.api.event.message.MessageCreateEvent

object CommonUtils {
  def startOfTheDayMillis: Long = LocalDate.now().atStartOfDay(ZoneId.systemDefault).toInstant.toEpochMilli

  def millisToFormattedDate(millis: Long, format: String = "dd/MM/yyyy") =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime.format(DateTimeFormatter.ofPattern(format))

  def secondsToFormattedDate(seconds: Long, format: String = "dd/MM/yyyy") =
    Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()).toLocalDateTime.format(DateTimeFormatter.ofPattern(format))

  def respondWithErrorMessage(event: MessageCreateEvent, content: String) = {
    val embed = new EmbedBuilder()
      .setColor(Color.decode("#7E0023"))
      .setTitle(content)
    CommonUtils.respond(event, embed)
  }

  def respond(event: MessageCreateEvent, message: String) = {
    if (event.getMessage.getChannel.canYouWrite) {
      event.getMessage.getChannel.sendMessage(message)
    } else {
      event.getMessage.getAuthor.asUser().ifPresent(u => u.sendMessage(message))
    }
  }

  def respond(event: MessageCreateEvent, embed: EmbedBuilder) = {
    if (event.getMessage.getChannel.canYouWrite) {
      event.getMessage.getChannel.sendMessage(embed)
    } else {
      event.getMessage.getAuthor.asUser().ifPresent(u => u.sendMessage(embed))
    }
  }
}
