package pl.touk.nussknacker.openapi.http

import io.circe.Json
import pl.touk.nussknacker.http.oauth2.{OAuth2ClientCredentialsAuthorization, OAuth2ClientCredentialsSupport}
import pl.touk.nussknacker.openapi.OAuth2ClientCredentials
import pl.touk.nussknacker.openapi.SwaggerService
import pl.touk.nussknacker.openapi.extractor.{HandleResponse, ServiceRequest}
import sttp.client3.SttpBackend
import sttp.model.StatusCode
import sttp.monad.MonadError
import sttp.monad.syntax._

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}

class SwaggerSttpService(baseUrl: URL, swaggerService: SwaggerService, codesToInterpretAsEmpty: List[StatusCode]) {

  private val oauth2ClientCredentialsSupport = new OAuth2ClientCredentialsSupport()

  def invoke(parameters: Map[String, Any])(
      implicit backend: SttpBackend[Future, Any],
      ec: ExecutionContext
  ): Future[AnyRef] = {
    implicit val monad: MonadError[Future] = backend.responseMonad
    val request                            = ServiceRequest(baseUrl, swaggerService, parameters)
    val sendResult =
      sendRequest(request).flatMap(SttpUtils.handleOptionalResponse[Future, Json](_, codesToInterpretAsEmpty))
    swaggerService.responseSwaggerType match {
      case Some(responseType) =>
        sendResult.map(json => HandleResponse(json, responseType))
      case None =>
        sendResult.map(_ => null)
    }
  }

  private def sendRequest(
      request: ServiceRequest.SwaggerRequestType
  )(implicit backend: SttpBackend[Future, Any], ec: ExecutionContext): Future[ServiceRequest.SwaggerResponseType] =
    swaggerService.securities.collect { case oauth2: OAuth2ClientCredentials => oauth2 } match {
      case Nil =>
        request.send(backend)
      case oauth2 :: Nil =>
        val oauth2Authorization = OAuth2ClientCredentialsAuthorization(
          oauth2.tokenUrl,
          oauth2.clientId,
          oauth2.clientSecret,
          oauth2.scope
        )
        oauth2ClientCredentialsSupport
          .sendRequestWithAuthorization(backend, request, oauth2Authorization)
          .fold(Future.failed, identity)
      case _ =>
        Future.failed(
          new IllegalArgumentException(
            s"Multiple OAuth2 client credentials security schemes are not supported for service ${swaggerService.name.value}"
          )
        )
    }

}
