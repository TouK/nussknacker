package pl.touk.nussknacker.openapi.http

import sttp.client3.{Response, ResponseException, SttpBackend}
import sttp.model.StatusCode

import scala.language.higherKinds

object SttpUtils {

  def handleOptionalResponse[R[_], T](
    response: Response[Either[ResponseException[String, io.circe.Error], Option[T]]],
    codesToInterpretAsEmpty: List[StatusCode]
  )(implicit backend: SttpBackend[R, Any]): R[Option[T]] = {
    val responseMonad = backend.responseMonad
    response.body match {
      case Left(_) if codesToInterpretAsEmpty.contains(response.code) => responseMonad.unit(None)
      case Left(error) =>
        responseMonad.error(
          new RuntimeException(s"Failed to invoke: ${error.toString}, status: ${response.code}", error)
        )
      case Right(resp) => responseMonad.unit(resp)
    }
  }

}
