package pl.touk.nussknacker.processCounts.influxdb

import io.circe.Decoder
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.sttp.SttpJson
import sttp.client._
import sttp.client.circe._
import sttp.client.monad.MonadError
import sttp.client.monad.syntax.MonadErrorOps

import scala.language.{higherKinds, implicitConversions}

class InfluxException(cause: Throwable) extends Exception(cause)
case class InvalidInfluxResponse(message: String, cause: Throwable) extends InfluxException(cause) {
  override def getMessage: String = s"Influx query failed with message '$message'"
}
case class InfluxHttpError(influxUrl: String, body: String, cause: Throwable) extends InfluxException(cause) {
  override def getMessage: String = s"Connection to influx failed with message '$body'"
}

//we use simplistic InfluxClient, as we only need queries
class SimpleInfluxClient[F[_]](config: InfluxConfig)(implicit backend: SttpBackend[F, Nothing, NothingT]) {
  implicit val monadError: MonadError[F] = backend.responseMonad

  def query(query: String): F[List[InfluxSeries]] = {
    def addAuth[T, S](req: Request[T, S]): RequestT[Identity, T, S] = (for {
      user <- config.user
      password <- config.password
    } yield req.auth.basic(user, password)).getOrElse(req)

    addAuth(basicRequest.get(config.uri.params("db" -> config.database, "q" -> query)))
      .response(asJson[InfluxResponse])
      .send()
      .flatMap(SttpJson.failureToError[F, InfluxResponse])
      .handleError {
        case ex: DeserializationError[_] => monadError.error(InvalidInfluxResponse(ex.getMessage, ex))
        case ex: HttpError => monadError.error(InfluxHttpError(config.influxUrl, ex.body, ex))
      }
      //we assume only one query
      .map(_.results.head.series)
  }

  def close(): F[Unit] = backend.close()

}

case class InfluxResponse(results: List[InfluxResult] = Nil)

object InfluxResponse {
  import io.circe.generic.extras.semiauto._
  implicit val decoder: Decoder[InfluxResponse] = deriveConfiguredDecoder
}

case class InfluxResult(series: List[InfluxSeries] = Nil)

object InfluxResult {
  import io.circe.generic.extras.semiauto._
  implicit val decoder: Decoder[InfluxResult] = deriveConfiguredDecoder
}

object InfluxSeries {

  import io.circe.generic.extras.semiauto._

  private implicit val numberOrStringDecoder: Decoder[Any] =
    Decoder.decodeBigDecimal.asInstanceOf[Decoder[Any]] or Decoder.decodeString.asInstanceOf[Decoder[Any]] or Decoder.const[Any]("")
  implicit val decoder: Decoder[InfluxSeries] = deriveConfiguredDecoder[InfluxSeries]

}

case class InfluxSeries(name: String, tags: Option[Map[String, String]], columns: List[String], values: List[List[Any]] = Nil) {
  val toMap: List[Map[String, Any]] = values.map(value => columns.zip(value).toMap)
}

