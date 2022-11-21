package pl.touk.nussknacker.openapi.http

import io.circe.Json
import pl.touk.nussknacker.openapi.SwaggerService
import pl.touk.nussknacker.openapi.extractor.{HandleResponse, ServiceRequest}
import sttp.client.SttpBackend
import sttp.client.monad.MonadError
import sttp.client.monad.syntax._
import sttp.model.StatusCode

import java.net.URL
import scala.language.higherKinds

class SwaggerSttpService(baseUrl: URL, swaggerService: SwaggerService) {

  private val codesToInterpretAsEmpty = swaggerService.codesToInterpretAsEmpty.map(new StatusCode(_))

  def invoke[F[_]](parameters: Map[String, Any])(implicit backend: SttpBackend[F, Nothing, Nothing]): F[AnyRef] = {
    implicit val monad: MonadError[F] = backend.responseMonad
    val request = ServiceRequest(baseUrl, swaggerService, parameters)
    val sendResult = request.send().flatMap(SttpUtils.handleOptionalResponse[F, Json](_, codesToInterpretAsEmpty))
    swaggerService.responseSwaggerType match {
      case Some(responseType) =>
        sendResult.map(json => HandleResponse(json, responseType))
      case None =>
        sendResult.map(_ => null)
    }
  }

}
