package pl.touk.nussknacker.processCounts.influxdb

import java.util.concurrent.TimeUnit

import sttp.client._
import sttp.client.circe._
import io.circe.Decoder
import pl.touk.nussknacker.engine.sttp.SttpJson

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class InfluxException(cause: Throwable) extends Exception(cause)
case class InvalidInfluxResponse(message: String, cause: Throwable) extends InfluxException(cause) {
  override def getMessage: String = s"Influx query failed with message '$message'"
}
case class InfluxHttpError(influxUrl: String, body: String, cause: Throwable) extends InfluxException(cause) {
  override def getMessage: String = s"Connection to influx failed with message '$body'"
}

//we use simplistic InfluxClient, as we only need queries
class SimpleInfluxClient(config: InfluxConfig)(implicit backend: SttpBackend[Future, Nothing, NothingT]) {

  private val uri = uri"${config.influxUrl}"

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

  def close(): Unit = Await.result(backend.close(), Duration(10, TimeUnit.SECONDS))

}

case class InfluxResponse(results: List[InfluxResult] = Nil)

object InfluxResponse {
  import io.circe.generic.semiauto._
  implicit val decoder: Decoder[InfluxResponse] = deriveDecoder
}

case class InfluxResult(series: List[InfluxSerie] = Nil)

object InfluxResult {
  import io.circe.generic.extras.Configuration
  import io.circe.generic.extras.semiauto._
  implicit val config: Configuration = Configuration.default.withDefaults
  implicit val decoder: Decoder[InfluxResult] = deriveDecoder
}

object InfluxSerie {

  import io.circe.generic.semiauto._

  private implicit val numberOrStringDecoder: Decoder[Any] =
    Decoder.decodeBigDecimal.asInstanceOf[Decoder[Any]] or Decoder.decodeString.asInstanceOf[Decoder[Any]] or Decoder.const[Any]("")

  implicit val decoder: Decoder[InfluxSerie] = deriveDecoder[InfluxSerie]

}

case class InfluxSerie(name: String, tags: Map[String, String], columns: List[String], values: List[List[Any]] = Nil) {
  val toMap: List[Map[String, Any]] = values.map(value => columns.zip(value).toMap)
}

