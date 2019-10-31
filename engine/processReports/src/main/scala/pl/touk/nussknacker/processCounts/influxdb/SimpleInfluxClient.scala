package pl.touk.nussknacker.processCounts.influxdb

import sttp.client._
import sttp.client.circe._
import io.circe.Decoder
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto._
import pl.touk.nussknacker.engine.sttp.SttpJson
import sttp.model.Uri

import scala.concurrent.{ExecutionContext, Future}

case class InfluxConfig(influxUrl: String, user: String, password: String, database: String = "esp")

class InfluxException(cause: Throwable) extends Exception(cause)
case class InvalidInfluxResponse(message: String, cause: Throwable) extends InfluxException(cause) {
  override def getMessage: String = s"Influx query failed with message '$message'"
}
case class InfluxHttpError(influxUrl: String, body: String, cause: Throwable) extends InfluxException(cause) {
  override def getMessage: String = s"Connection to influx failed with message '$body'"
}

//we use simplistic InfluxClient, as we only need queries
class SimpleInfluxClient(config: InfluxConfig)(implicit backend: SttpBackend[Future, Nothing, NothingT]) {

  private val uri = Uri.parse(config.influxUrl).get

  def query(query: String)(implicit ec: ExecutionContext): Future[List[InfluxSerie]] = {
    basicRequest.get(uri.params("db" -> config.database, "q" -> query))
      .auth.basic(config.user, config.password)
      .response(asJson[InfluxResponse])
      .send()
      .flatMap(SttpJson.failureToFuture[InfluxResponse])
      .recoverWith {
        case ex: DeserializationError[_] => Future.failed(InvalidInfluxResponse(ex.getMessage, ex))
        case ex: HttpError => Future.failed(InfluxHttpError(config.influxUrl, ex.body, ex))
      }
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

