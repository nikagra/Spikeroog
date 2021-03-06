package com.mikitahradovich.spikeroog

import com.google.inject.Guice
import com.mikitahradovich.spikeroog.air.AirQualityListener
import com.mikitahradovich.spikeroog.misc.EchoListener
import com.mikitahradovich.spikeroog.releases.ReleasesListener
import com.typesafe.config.Config
import net.codingwell.scalaguice.InjectorExtensions._
import org.apache.logging.log4j.scala.Logging
import org.javacord.api.DiscordApiBuilder

object Spikeroog extends App with Logging {

  logger.info("Starting application...")

  val injector = Guice.createInjector(new MainModule())


  val config = injector.instance[Config]
  val token = config.getString("app.discord.token")

  logger.info("Logging in to Discord...")

  val api = new DiscordApiBuilder().setToken(token).login.join

  logger.info("Successfully logged in. Registering listeners...")

  val echoListener = injector.instance[EchoListener]
  api.addMessageCreateListener(echoListener)

  val airQualityListener = injector.instance[AirQualityListener]
  api.addMessageCreateListener(airQualityListener)

  val releasesListener = injector.instance[ReleasesListener]
  api.addMessageCreateListener(releasesListener)

  logger.info("Listeners registered")

}
