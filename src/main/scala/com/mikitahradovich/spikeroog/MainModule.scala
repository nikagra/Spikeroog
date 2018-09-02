package com.mikitahradovich.spikeroog

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.google.inject
import com.google.inject.{AbstractModule, Provides}
import com.mikitahradovich.spikeroog.air.AirQualityListener
import com.mikitahradovich.spikeroog.common.FileReader
import com.mikitahradovich.spikeroog.misc.EchoListener
import com.mikitahradovich.spikeroog.releases.ReleasesListener
import com.typesafe.config.{Config, ConfigFactory}
import net.codingwell.scalaguice.ScalaModule

import scala.concurrent.ExecutionContext

class MainModule extends AbstractModule with ScalaModule {

  override def configure(): Unit = {
    bind[ActorSystem].toInstance(ActorSystem())
    bind[FileReader]

    bind[EchoListener]
  }

  @Provides
  @inject.Singleton
  def getActorMaterializer(implicit actorSystem: ActorSystem): ActorMaterializer =
    ActorMaterializer()

  @Provides
  @inject.Singleton
  def getExecutionContext(actorSystem: ActorSystem): ExecutionContext =
    actorSystem.dispatcher

  @Provides
  @inject.Singleton
  def getConfig: Config =
    ConfigFactory.load()

  @Provides
  @inject.Singleton
  def getAirQualityListener(config: Config,
                            fileReader: FileReader)(
                            implicit actorSystem: ActorSystem,
                            actorMaterializer: ActorMaterializer,
                            executionContext: ExecutionContext): AirQualityListener =
    new AirQualityListener(config, fileReader, Http())

  @Provides
  @inject.Singleton
  def getReleasesListener(config: Config,
                            fileReader: FileReader)(
                             implicit actorSystem: ActorSystem,
                             actorMaterializer: ActorMaterializer,
                             executionContext: ExecutionContext): ReleasesListener =
    new ReleasesListener(config, fileReader, Http())

}
