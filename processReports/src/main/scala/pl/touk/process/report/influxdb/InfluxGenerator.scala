package pl.touk.process.report.influxdb

import java.time.{LocalDateTime, ZoneId}
import java.util.UUID

import argonaut.{DecodeJson, DecodeResult}
import com.ning.http.client.AsyncHttpClient
import com.typesafe.scalalogging.LazyLogging
import dispatch.Http
import pl.touk.esp.engine.util.service.{AuditDispatchClientImpl, LogCorrelationId}

import scala.concurrent.Future

class InfluxGenerator(url: String, user: String, password: String, dbName: String, env: String = "test") extends LazyLogging {

  import argonaut.ArgonautShapeless._

  import scala.concurrent.ExecutionContext.Implicits.global

  val httpClient = new AuditDispatchClientImpl(http = Http(new AsyncHttpClient()))

  implicit val numberOrStringDecoder = DecodeJson.apply[Any] { cursor =>
    val focused = cursor.focus
    val bigDecimalDecoder = implicitly[DecodeJson[BigDecimal]].asInstanceOf[DecodeJson[Any]]
    val stringDecoder = implicitly[DecodeJson[String]].asInstanceOf[DecodeJson[Any]]
    DecodeResult.ok(focused.as(bigDecimalDecoder).toOption.getOrElse(focused.as(stringDecoder).toOption.get))
  }

  case class InfluxResponse(results: List[InfluxResult] = List())
  case class InfluxResult(series: List[InfluxSerie] = List())
  case class InfluxSerie(name: String, columns: List[String], values: List[List[Any]] = List())

  def query(processName: String, metricName: String, dateFrom: LocalDateTime, dateTo: LocalDateTime): Future[Map[String, Long]] = {
    val start = dateFrom
    val stop = dateTo

    def query(date: LocalDateTime) = {
      //we use epoch seconds to avoid time zone problems... in influx
      val from = toEpochSeconds(date)

      val query = s"""select action, first(value) as value from "$metricName.count" where process = '$processName' """ +
        s"and time >= ${from}s and env = '$env' group by slot, action"

      implicit val id = LogCorrelationId(UUID.randomUUID().toString)
      val req = dispatch.url(url)
        .addQueryParameter("db", dbName)
        .addQueryParameter("q", query)
        .as_!(user, password)

      httpClient.getJsonAsObject[InfluxResponse](req)
        .map { qr =>

          qr.results.head.series
            .map(r => (r.values.head.lift(1).getOrElse("UNKNOWN"), r.values.head.lift(2).getOrElse(0L)))
            .groupBy(_._1.asInstanceOf[String]).mapValues(_.map(_._2.asInstanceOf[Number].longValue()).sum)
        }.map(_.map {
        case (k,v) =>
          //jak to zrobic w asyncHttpClient?
          val conv = new String(k.getBytes("ISO-8859-1"), "UTF-8")
          (conv, v)
      })
    }

    for {
      valuesAtEnd <- query(stop)
      valuesAtStart <- query(start)
    } yield valuesAtEnd.map {
      case (key, value) => key -> (value - valuesAtStart.getOrElse(key, 0L))
    }
  }

  def close() : Unit = {
    httpClient.shutdown()
  }

  private def toEpochSeconds(d: LocalDateTime): Long = {
    d.atZone(ZoneId.systemDefault()).toInstant.toEpochMilli / 1000
  }
}