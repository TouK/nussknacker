package pl.touk.nussknacker.processCounts.influxdb

import com.softwaremill.sttp._
import com.softwaremill.sttp.circe._
import io.circe.Decoder
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto._
import pl.touk.nussknacker.engine.sttp.SttpJson

import scala.concurrent.{ExecutionContext, Future}

case class InfluxConfig(influxUrl: String, user: String, password: String, database: String = "esp")

//we use simplistic InfluxClient, as we only need queries
class SimpleInfluxClient(config: InfluxConfig)(implicit backend: SttpBackend[Future, Nothing]) {

  private val uri = Uri.parse(config.influxUrl).get

  def query(query: String)(implicit ec: ExecutionContext): Future[List[InfluxSerie]] = {
    sttp.get(uri.params("db" -> config.database, "q" -> query))
      .auth.basic(config.user, config.password)
      .response(asJson[InfluxResponse])
      .send()
      .flatMap(SttpJson.failureToFuture[InfluxResponse])
      //we assume only one query
      .map(_.results.head.series)
  }

  def close(): Unit = {
    backend.close()
  }
}

@JsonCodec(decodeOnly = true) case class InfluxResponse(results: List[InfluxResult] = List())

@JsonCodec(decodeOnly = true) case class InfluxResult(series: List[InfluxSerie] = List())

object InfluxSerie {

  private implicit val numberOrStringDecoder: Decoder[Any] =
    Decoder.decodeBigDecimal.asInstanceOf[Decoder[Any]] or Decoder.decodeString.asInstanceOf[Decoder[Any]] or Decoder.const[Any]("")

  implicit val decoder: Decoder[InfluxSerie] = deriveDecoder[InfluxSerie]

}

case class InfluxSerie(name: String, tags: Map[String, String], columns: List[String], values: List[List[Any]] = List()) {
  val toMap: List[Map[String, Any]] = values.map(value => columns.zip(value).toMap)
}

