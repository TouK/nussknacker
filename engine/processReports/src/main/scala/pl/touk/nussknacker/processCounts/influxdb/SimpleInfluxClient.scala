package pl.touk.nussknacker.processCounts.influxdb

import argonaut.{DecodeJson, DecodeResult}
import dispatch.Http
import dispatch._
import io.circe.Decoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.dispatch.LoggingDispatchClient
import pl.touk.nussknacker.engine.dispatch.utils.asCirce

import scala.concurrent.{ExecutionContext, Future}

case class InfluxConfig(influxUrl: String, user: String, password: String, database: String = "esp")

//we use simplistic InfluxClient, as we only need queries
class SimpleInfluxClient(config: InfluxConfig) {

  private val httpClient = LoggingDispatchClient(classOf[InfluxGenerator].getSimpleName, Http.default)

  def query(query: String)(implicit ec: ExecutionContext): Future[List[InfluxSerie]] = {
    httpClient {
      dispatch
        .url(config.influxUrl) <<? Map("db" -> config.database, "q" -> query) as_!(config.user, config.password) OK
        asCirce[InfluxResponse]
    }.map { qr =>
      //we assume only one query
      qr.results.head.series
    }
  }

  def close(): Unit = {
    httpClient.shutdown()
  }
}

@JsonCodec(decodeOnly = true) case class InfluxResponse(results: List[InfluxResult] = List())

@JsonCodec(decodeOnly = true) case class InfluxResult(series: List[InfluxSerie] = List())

object InfluxSerie {

  private implicit val numberOrStringDecoder: Decoder[Any]
    = Decoder.decodeBigDecimal.asInstanceOf[Decoder[Any]] or Decoder.decodeString.asInstanceOf[Decoder[Any]] or Decoder.const("").asInstanceOf[Decoder[Any]]

}

@JsonCodec(decodeOnly = true) case class InfluxSerie(name: String, tags: Map[String, String], columns: List[String], values: List[List[Any]] = List()) {
  val toMap: List[Map[String, Any]] = values.map(value => columns.zip(value).toMap)
}

