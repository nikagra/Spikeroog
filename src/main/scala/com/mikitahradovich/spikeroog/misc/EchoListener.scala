package com.mikitahradovich.spikeroog.misc

import com.mikitahradovich.spikeroog.common.CommonUtils
import org.javacord.api.event.message.MessageCreateEvent
import org.javacord.api.listener.message.MessageCreateListener

class EchoListener extends MessageCreateListener{
  override def onMessageCreate(event: MessageCreateEvent): Unit =
    if (event.getMessage.getContent.startsWith("!echo")) CommonUtils.respond(event, event.getMessage.getContent.stripPrefix("!echo").trim)
}
