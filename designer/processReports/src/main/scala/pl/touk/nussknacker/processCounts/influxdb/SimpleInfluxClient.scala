package pl.touk.nussknacker.processCounts.influxdb

import io.circe.Decoder
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.http.backend.SttpJson
import sttp.client3._
import sttp.client3.circe._
import sttp.monad.MonadError
import sttp.monad.syntax.MonadErrorOps

import scala.language.{higherKinds, implicitConversions}

class InfluxException(cause: Throwable) extends Exception(cause)

final case class InvalidInfluxResponse(message: String, cause: Throwable) extends InfluxException(cause) {
  override def getMessage: String = s"Influx query failed with message '$message'"
}

final case class InfluxHttpError(influxUrl: String, body: String, cause: Throwable) extends InfluxException(cause) {
  override def getMessage: String = s"Connection to influx failed with message '$body'"
}

//we use simplistic InfluxClient, as we only need queries
class SimpleInfluxClient[F[_]](config: InfluxConfig)(implicit backend: SttpBackend[F, Any]) {
  implicit val monadError: MonadError[F] = backend.responseMonad

  private implicit class RequestExtensions[U[_], T, -R](val request: RequestT[U, T, R]) {

    def withAuthentication(): RequestT[U, T, R] = (for {
      user     <- config.user
      password <- config.password
    } yield request.auth.basic(user, password)).getOrElse(request)

  }

  def query(query: String): F[List[InfluxSeries]] = {
    basicRequest
      .get(config.uri.addParams("db" -> config.database, "q" -> query))
      .withAuthentication()
      .headers(config.additionalHeaders)
      .response(asJson[InfluxResponse])
      .send(backend)
      .flatMap(SttpJson.failureToError[F, InfluxResponse])
      .handleError {
        case ex: DeserializationException[_] => monadError.error(InvalidInfluxResponse(ex.getMessage, ex))
        case ex: HttpError[_]                => monadError.error(InfluxHttpError(config.influxUrl, s"${ex.body}", ex))
      }
      // we assume only one query
      .map(_.results.head.series)
  }

}

final case class InfluxResponse(results: List[InfluxResult] = Nil)

object InfluxResponse {
  import io.circe.generic.extras.semiauto._
  implicit val decoder: Decoder[InfluxResponse] = deriveConfiguredDecoder
}

final case class InfluxResult(series: List[InfluxSeries] = Nil)

object InfluxResult {
  import io.circe.generic.extras.semiauto._
  implicit val decoder: Decoder[InfluxResult] = deriveConfiguredDecoder
}

object InfluxSeries {

  import io.circe.generic.extras.semiauto._

  private implicit val numberOrStringDecoder: Decoder[Any] =
    Decoder.decodeBigDecimal.asInstanceOf[Decoder[Any]] or Decoder.decodeString.asInstanceOf[Decoder[Any]] or Decoder
      .const[Any]("")

  implicit val decoder: Decoder[InfluxSeries] = deriveConfiguredDecoder[InfluxSeries]

}

final case class InfluxSeries(
    name: String,
    tags: Option[Map[String, String]],
    columns: List[String],
    values: List[List[Any]] = Nil
) {
  val toMap: List[Map[String, Any]] = values.map(value => columns.zip(value).toMap)
}
