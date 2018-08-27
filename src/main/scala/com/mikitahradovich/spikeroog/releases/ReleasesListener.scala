package com.mikitahradovich.spikeroog.releases

import java.awt.Color

import akka.actor.ActorSystem
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.google.inject.Inject
import com.mikitahradovich.spikeroog.common.{CommonUtils, FileReader}
import com.typesafe.config.Config
import org.apache.logging.log4j.scala.Logging
import org.javacord.api.entity.message.embed.EmbedBuilder
import org.javacord.api.event.message.MessageCreateEvent
import org.javacord.api.listener.message.MessageCreateListener
import spray.json.{DefaultJsonProtocol, _}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class Release(game: Either[Long, Game], platform: Long, date: Long)

case class Game(id: Long, name: String)

trait GameReleasesJsonSupport extends DefaultJsonProtocol with Logging {

  implicit val gameJsonFormat = jsonFormat2(Game)
  implicit val releaseJsonFormat = jsonFormat3(Release)
}

class ReleasesListener @Inject()(
                                  config: Config,
                                  fileReader: FileReader,
                                  http: HttpExt)(
                                  implicit val actorSystem: ActorSystem,
                                  implicit val actorMaterializer: ActorMaterializer,
                                  implicit val executionContext: ExecutionContext) extends MessageCreateListener with GameReleasesJsonSupport {

  private val token = config.getString("app.service.igdb.token")
  private val groupedPlatforms: Map[Long, String] = fileReader
    .readCsvFile(config.getString("app.service.igdb.platforms"))
    .groupBy(_.head.toLong)
    .mapValues(_.head.last)

  override def onMessageCreate(event: MessageCreateEvent): Unit = {
    val content = event.getMessage.getContent
    if (content.equalsIgnoreCase("!premiery")) {

      logger.debug(s"Processing request from ${event.getMessage.getAuthor.getDiscriminatedName}")

      val responseFuture: Future[HttpResponse] = http.singleRequest(prepareReleasesRequest)

      responseFuture
        .onComplete {
          case Success(response) =>
            val responseAsString: Future[String] = Unmarshal(response.entity).to[String]
            responseAsString.onComplete {
              case Success(value) =>
                logger.debug(s"Received releases response with body $value")
                val releases: List[Release] = value.parseJson.convertTo[List[Release]]

                fetchGamesForReleases(releases)
                  .onComplete {
                    case Success(games) =>
                      println(games)
                      val gamesGrouped = games.filter(t => t.isSuccess).map(_.get).groupBy(_.id)
                      val releasesGrouped = processReleasesResponse(releases).filter(r => gamesGrouped.get(r._1.left.get).isDefined)

                      val embed = prepareReleasesReport(releasesGrouped, gamesGrouped)
                      event.getMessage.getAuthor.asUser().ifPresent(u => u.sendMessage(embed))
                    case Failure(ex) =>
                      ex.printStackTrace()
                      logger.error(s"Something went wrong (${ex.getMessage})")
                  }
            }
          case Failure(ex) =>
            event.getMessage.getAuthor.asUser().ifPresent(u => u.sendMessage(s"Something went wrong (${ex.getMessage})"))
        }
    }
  }

  private def fetchGamesForReleases(releases: List[Release]) = {
    Future.sequence(releases
      .filter(_.game.isLeft)
      .map(v => http.singleRequest(prepareGameRequest(v.game.left.get)))
      .map(v => v.flatMap(r => Unmarshal(r.entity).to[String]))
      .map(v => {
        v.map(s => s.parseJson.convertTo[List[Game]])
          .filter(l => l.nonEmpty)
          .map(_.head)
      })
      .map(_.transform(Success(_))))
      .map(_.filter(_.isSuccess))
  }

  private def prepareReleasesReport(releasesGrouped: List[(Either[Long, Game], String)], gamesGrouped: Map[Long, List[Game]]) = {
    val embed = new EmbedBuilder()
      .setTitle("Premiery :video_game:")
      .setColor(Color.blue)
    if (releasesGrouped.isEmpty) {
      embed.addInlineField("...", "...")
    } else {
      releasesGrouped.foreach(r => {
        gamesGrouped(r._1.left.get).map(g => embed.addField(g.name, r._2))
      })
    }
    embed
  }

  private def prepareReleasesRequest = {
    HttpRequest(
      uri = Uri("https://api-endpoint.igdb.com/release_dates/")
        .withQuery(Query(
          ("fields", "game,game.name,platform,date"),
          ("order", "date:asc"),
          ("filter[date][gt]", System.currentTimeMillis().toString),
          ("filter[platform][any]", groupedPlatforms.keySet.map(_.toString).reduceLeft((acc, p) => s"$acc,$p")),
          ("limit", "10")))
    ).withHeaders(RawHeader("user-key", token), RawHeader("Accept", MediaTypes.`application/json`.toString()))
  }

  private def prepareGameRequest(id: Long) = {
    HttpRequest(
      uri = Uri(s"https://api-endpoint.igdb.com/games/$id")
        .withQuery(Query(
          ("fields", "id,name")))
    ).withHeaders(RawHeader("user-key", token), RawHeader("Accept", MediaTypes.`application/json`.toString()))
  }

  private def processReleasesResponse(releases: List[Release]) = {
    releases
      .filter(_.game.isLeft)
      .distinct // There are duplicated entries sometimes
      .groupBy(r => r.game)
      .mapValues(_.groupBy(_.date)
        .mapValues(_.map(r => groupedPlatforms.getOrElse(r.platform, "Unknown"))
          .reduceLeft((acc, p) => s"$acc, $p"))
        .toList.
        groupBy(_._2).map(_._2.head)) // Remove occasional duplicated releases with contradicting dates
      .toList
      .sortBy(_._2.keys.min)
      .map(e => (e._1, e._2
        .map(r => s"${CommonUtils.millisToFormattedDate(r._1)} na ${r._2}")
        .reduceLeft((acc, p) => s"$acc oraz $p")))
  }
}
