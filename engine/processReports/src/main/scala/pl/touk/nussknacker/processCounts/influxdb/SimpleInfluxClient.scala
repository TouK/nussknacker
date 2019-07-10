package pl.touk.nussknacker.processCounts.influxdb

import argonaut.{DecodeJson, DecodeResult}
import dispatch.Http
import dispatch._
import pl.touk.nussknacker.engine.dispatch.LoggingDispatchClient
import pl.touk.nussknacker.engine.dispatch.utils.asJson
import argonaut.ArgonautShapeless._
import argonaut.Argonaut._

import scala.concurrent.{ExecutionContext, Future}

case class InfluxConfig(influxUrl: String, user: String, password: String, database: String = "esp")

//we use simplistic InfluxClient, as we only need queries
class SimpleInfluxClient(config: InfluxConfig) {

  private val httpClient = LoggingDispatchClient(classOf[InfluxGenerator].getSimpleName, Http.default)

  def query(query: String)(implicit ec: ExecutionContext): Future[List[InfluxSerie]] = {
    httpClient {
      dispatch
        .url(config.influxUrl) <<? Map("db" -> config.database, "q" -> query) as_!(config.user, config.password) OK
        asJson[InfluxResponse]
    }.map { qr =>
      //we assume only one query
      qr.results.head.series
    }
  }

  def close(): Unit = {
    httpClient.shutdown()
  }
}

case class InfluxResponse(results: List[InfluxResult] = List())

case class InfluxResult(series: List[InfluxSerie] = List())

object InfluxSerie {

  private implicit val numberOrStringDecoder: DecodeJson[Any] = DecodeJson.apply[Any] { cursor =>
    val focused = cursor.focus
    val bigDecimalDecoder = implicitly[DecodeJson[BigDecimal]].asInstanceOf[DecodeJson[Any]]
    val stringDecoder = implicitly[DecodeJson[String]].asInstanceOf[DecodeJson[Any]]
    DecodeResult.ok(focused.as(bigDecimalDecoder).toOption.getOrElse(focused.as(stringDecoder).toOption.getOrElse("")))
  }

  implicit val codec: DecodeJson[InfluxSerie] = DecodeJson.derive[InfluxSerie]

}

case class InfluxSerie(name: String, tags: Map[String, String], columns: List[String], values: List[List[Any]] = List()) {
  val toMap: List[Map[String, Any]] = values.map(value => columns.zip(value).toMap)
}

