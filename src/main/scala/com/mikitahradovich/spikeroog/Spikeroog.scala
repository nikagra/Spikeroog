package com.mikitahradovich.spikeroog

import com.google.inject.Guice
import com.mikitahradovich.spikeroog.air.AirQualityListener
import com.typesafe.config.Config
import org.apache.logging.log4j.scala.Logging
import org.javacord.api.DiscordApiBuilder

object Spikeroog extends App with Logging {

  logger.info("Starting application...")

  val injector = Guice.createInjector(new MainModule())

  import net.codingwell.scalaguice.InjectorExtensions._
  val config = injector.instance[Config]
  val airQualityListener = injector.instance[AirQualityListener]

  val token = config.getString("app.discord.token")

  logger.info("Logging in to Discord...")

  val api = new DiscordApiBuilder().setToken(token).login.join

  logger.info("Successfully logged in. Registering listeners...")

  api.addMessageCreateListener(event =>
    if (event.getMessage.getContent.startsWith("!echo")) event.getChannel.sendMessage(event.getMessage.getContent.stripPrefix("!echo").trim)
  )

  api.addMessageCreateListener(airQualityListener)

  logger.info("Listeners registered")

}
