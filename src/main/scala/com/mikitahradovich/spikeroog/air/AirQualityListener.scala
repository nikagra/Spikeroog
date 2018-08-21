package com.mikitahradovich.spikeroog.air

import java.awt.Color
import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, ZoneId}

import akka.actor.ActorSystem
import akka.http.scaladsl.{Http, HttpExt}
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.google.inject.Inject
import com.mikitahradovich.spikeroog.common.FileReader
import com.typesafe.config.Config
import org.apache.logging.log4j.scala.Logging
import org.javacord.api.entity.message.embed.EmbedBuilder
import org.javacord.api.event.message.MessageCreateEvent
import org.javacord.api.listener.message.MessageCreateListener
import spray.json.{DefaultJsonProtocol, _}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class Result(success: Boolean, data: Data)

case class Data(title: String, series: List[Series])

case class Series(paramId: String, paramLabel: String, unit: String, aggType: String, data: List[List[String]])

case class AirQualityQuery(meansType: String, viewType: String, dateRange: String, date: String, viewEntityId: String, channels: List[Long])

trait AirQualityJsonSupport extends DefaultJsonProtocol with Logging {

  implicit val seriesJsonFormat = jsonFormat5(Series)
  implicit val dataJsonSupport = jsonFormat2(Data)
  implicit val resultJsonSupport = jsonFormat2(Result)
  implicit val queryJsonSupport = jsonFormat6(AirQualityQuery)
}

class AirQualityListener @Inject()(
                                    config: Config,
                                    fileReader: FileReader,
                                    http: HttpExt)(
                                    implicit val actorSystem: ActorSystem,
                                    implicit val actorMaterializer: ActorMaterializer,
                                    implicit val executionContext: ExecutionContext) extends MessageCreateListener with AirQualityJsonSupport {

  private case class IndexEntry(categoryId: Long, categoryName: String, color: Color, param: String, value: Float)

  private case class ReportEntry(paramId: String, paramLabel: String, unit: String, timestamp: Long, value: Float, indexEntry: IndexEntry)

  private val indexGrouped = groupedIndexData(fileReader.readCsvFile(config.getString("app.service.air.index")))

  override def onMessageCreate(event: MessageCreateEvent): Unit = {
    val content = event.getMessage.getContent
    if (content.equalsIgnoreCase("!jakość")) {

      logger.debug(s"Processing request from ${event.getMessage.getAuthor.getDiscriminatedName}")

      val responseFuture: Future[HttpResponse] = http.singleRequest(prepareRequest())

      responseFuture.onComplete {
        case Success(res) =>

          val responseAsString: Future[String] = Unmarshal(res.entity).to[String]
          responseAsString.onComplete {
            case Success(value) =>
              logger.debug(s"Received response from WIOŚ with body $value")
              val result = value.parseJson.convertTo[Result]

              if (result.success) {
                val data = processResponseData(result, indexGrouped)

                val embed: EmbedBuilder = prepareResponseMessage(result, data)
                event.getMessage.getAuthor.asUser().ifPresent(u => u.sendMessage(embed))
              } else {
                new EmbedBuilder()
                  .setColor(Color.decode("#7E0023"))
                  .setTitle("Nie mogę pobrać danych. Przepraszam :bow:")
              }
            case Failure(ex) => logger.error(s"Something wrong (${ex.getMessage})")
          }
        case Failure(ex) => logger.error(s"Something wrong (${ex.getMessage})")
      }
    }
  }

  private def prepareRequest(date: LocalDate = LocalDate.now()) = {
    val formattedDate = date.format(DateTimeFormatter.ofPattern("dd.MM.yyy"))
    val stationId = config.getLong("app.service.air.station.id").toString
    val channels = config.getLongList("app.service.air.station.channels").asScala.mkString(",")

    val query =
      s"""{
         |"measType":"Auto",
         |"viewType":"Station",
         |"dateRange":"Day",
         |"date":"$formattedDate",
         |"viewTypeEntityId":"$stationId",
         |"channels":[$channels]}""".stripMargin
    HttpRequest(method = HttpMethods.POST,
      uri = Uri("http://air.wroclaw.pios.gov.pl/dane-pomiarowe/pobierz")
        .withQuery(Query(("query", query)))
    )
  }

  private def groupedIndexData(index: Stream[List[String]]): Map[String, Stream[IndexEntry]] = {
    index
      .drop(1)
      .flatMap(line => index.head.drop(3).zip(line.drop(3)).zip(List.fill(line.size - 3)((line(0), line(1), line(2))))
        .map(t => IndexEntry(
          categoryId = t._2._1.toLong,
          categoryName = t._2._2,
          param = t._1._1,
          value = t._1._2.toFloat,
          color = Color.decode(t._2._3))))
      .groupBy(_.param)
      .mapValues(_.sortBy(_.value)(Ordering[Float].reverse))
  }

  private def processResponseData(result: Result, groupedIndex: Map[String, Stream[IndexEntry]]) = {
    result.data.series
      .filter(s => s.aggType.equals("A1h"))
      .flatMap(s => s.data.map { case ts :: v :: Nil => (ts.toLong, s.paramId, s.paramLabel, s.unit, v.toFloat) })
      .groupBy(_._1)
      .toList
      .map(e =>
        (e._1, e._2.map(x => ReportEntry(
          paramId = x._2,
          paramLabel = x._3,
          unit = x._4,
          timestamp = x._1,
          value = x._5,
          indexEntry = groupedIndex(x._2).collectFirst { case i if i.value < x._5 => i }.get))))
      .maxBy(_._1)
  }

  private def prepareResponseMessage(result: Result, data: (Long, List[ReportEntry])) = {
    val embed = new EmbedBuilder()
      .setTitle(result.data.title)
      .setColor(data._2.maxBy(_.indexEntry.categoryId).indexEntry.color)
      .setUrl("http://air.wroclaw.pios.gov.pl/dane-pomiarowe/automatyczne/stacja/13/parametry/wszystkie")
    embed.addField("Czas pomiaru", Instant.ofEpochSecond(data._1).atZone(ZoneId.systemDefault()).toLocalDateTime.format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")))
    data._2.foreach(m => embed.addField(m.paramLabel, f"${m.value}%1.1f ${m.unit} (${m.indexEntry.categoryName})"))
    embed
  }

}
