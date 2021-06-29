package pl.touk.nussknacker.openapi.http

import sttp.client.{Response, ResponseError, SttpBackend}
import sttp.model.StatusCode
import scala.language.higherKinds

object SttpUtils {

  def handleOptionalResponse[R[_], T](response: Response[Either[ResponseError[io.circe.Error], Option[T]]])(implicit backend: SttpBackend[R, Nothing, Nothing]): R[Option[T]] = {
    val responseMonad = backend.responseMonad
    response.body match {
      case Left(_) if response.code == StatusCode.NotFound => responseMonad.unit(None)
      case Left(error) => responseMonad.error(new RuntimeException(s"Failed to invoke: ${error.toString}, status: ${response.code}", error))
      case Right(resp) => responseMonad.unit(resp)
    }
  }

}
