package pl.touk.nussknacker.openapi.http

import io.circe.Json
import pl.touk.nussknacker.openapi.SwaggerService
import pl.touk.nussknacker.openapi.extractor.{HandleResponse, ServiceRequest}
import sttp.client3.SttpBackend
import sttp.model.StatusCode
import sttp.monad.MonadError
import sttp.monad.syntax._

import java.net.URL
import scala.language.higherKinds

class SwaggerSttpService(baseUrl: URL, swaggerService: SwaggerService, codesToInterpretAsEmpty: List[StatusCode]) {

  def invoke[F[_]](parameters: Map[String, Any])(implicit backend: SttpBackend[F, Any]): F[AnyRef] = {
    implicit val monad: MonadError[F] = backend.responseMonad
    val request = ServiceRequest(baseUrl, swaggerService, parameters)
    val sendResult = request.send(backend).flatMap(SttpUtils.handleOptionalResponse[F, Json](_, codesToInterpretAsEmpty))
    swaggerService.responseSwaggerType match {
      case Some(responseType) =>
        sendResult.map(json => HandleResponse(json, responseType))
      case None =>
        sendResult.map(_ => null)
    }
  }

}
