package pl.touk.process.report.influxdb

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId, ZonedDateTime}

import argonaut.{DecodeJson, DecodeResult}
import com.typesafe.scalalogging.LazyLogging
import dispatch._
import pl.touk.nussknacker.engine.dispatch.LoggingDispatchClient
import pl.touk.nussknacker.engine.dispatch.utils._

import scala.concurrent.Future

class InfluxGenerator(url: String, user: String, password: String, dbName: String, env: String = "test") extends LazyLogging {

  import argonaut.ArgonautShapeless._

  import scala.concurrent.ExecutionContext.Implicits.global

  val httpClient = LoggingDispatchClient(classOf[InfluxGenerator], Http())

  implicit val numberOrStringDecoder = DecodeJson.apply[Any] { cursor =>
    val focused = cursor.focus
    val bigDecimalDecoder = implicitly[DecodeJson[BigDecimal]].asInstanceOf[DecodeJson[Any]]
    val stringDecoder = implicitly[DecodeJson[String]].asInstanceOf[DecodeJson[Any]]
    DecodeResult.ok(focused.as(bigDecimalDecoder).toOption.getOrElse(focused.as(stringDecoder).toOption.getOrElse("")))
  }

  def query(processName: String, metricName: String, dateFrom: LocalDateTime, dateTo: LocalDateTime): Future[Map[String, Long]] = {
    val start = dateFrom
    val stop = dateTo

    def queryForDate(date: LocalDateTime) = {
      //we use epoch seconds to avoid time zone problems... in influx
      val from = toEpochSeconds(date)
      //two hour window is for possible delays in sending metrics from taskmanager to jobmanager (or upd sending problems...)
      //it's VERY unclear how large it should be. If it's too large, we may overlap with end and still generate
      //bad results...
      val queryString =
      s"""select action, first(value) as value from "$metricName.count" where process = '$processName' """ +
        s"and time >= ${from}s and time < ${from}s + 2h and env = '$env' group by slot, action"

      query(queryString).map(
        _.map(r => (r.values.head.lift(1).getOrElse("UNKNOWN"), r.values.head.lift(2).getOrElse(0L)))
          .groupBy(_._1.asInstanceOf[String]).mapValues(_.map(_._2.asInstanceOf[Number].longValue()).sum))
        .map(_.map {
          case (k, v) =>
            //how can this be handled in asynclient?
            val conv = new String(k.getBytes("ISO-8859-1"), "UTF-8")
            (conv, v)
        })
    }

    for {
      valuesAtEnd <- queryForDate(stop)
      valuesAtStart <- queryForDate(start)
    } yield valuesAtEnd.map {
      case (key, value) => key -> (value - valuesAtStart.getOrElse(key, 0L))
    }
  }

  private def query(query: String): Future[List[InfluxSerie]] = {
    httpClient {
      dispatch
        .url(url) <<? Map("db" -> dbName, "q" -> query) as_!(user, password) OK
        asJson[InfluxResponse]
    }.map { qr =>
      qr.results.head.series
    }
  }

  private def toEpochSeconds(d: LocalDateTime): Long = {
    d.atZone(ZoneId.systemDefault()).toInstant.toEpochMilli / 1000
  }

  def detectRestarts(processName: String, dateFrom: LocalDateTime, dateTo: LocalDateTime): Future[List[LocalDateTime]] = {
    val from = toEpochSeconds(dateFrom)
    val to = toEpochSeconds(dateTo)
    val queryString =
      //TODO: is it always correct? Will it be performant enough for large (many days) ranges?
      s"""SELECT derivative(value) FROM "source.count" WHERE
         |"process" = '$processName' AND env = '$env'
         | AND time >= ${from}s and time < ${to}s GROUP BY slot""".stripMargin
    query(queryString).map { series =>
      series.headOption.map(readRestartsFromSourceCounts).getOrElse(List())
    }
  }

  private def readRestartsFromSourceCounts(sourceCounts: InfluxSerie) : List[LocalDateTime] = {
    val restarts = sourceCounts.values.collect {
      case (date:String)::(derivative:BigDecimal)::Nil if derivative < 0 => parseInfluxDate(date)
    }
    restarts
  }

  private def parseInfluxDate(date:String) : LocalDateTime =
    ZonedDateTime.parse(date, DateTimeFormatter.ISO_ZONED_DATE_TIME).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime

  def close(): Unit = {
    httpClient.shutdown()
  }

  case class InfluxResponse(results: List[InfluxResult] = List())

  case class InfluxResult(series: List[InfluxSerie] = List())

  case class InfluxSerie(name: String, columns: List[String], values: List[List[Any]] = List())
}