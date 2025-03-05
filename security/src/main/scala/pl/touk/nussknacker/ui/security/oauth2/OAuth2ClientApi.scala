package pl.touk.nussknacker.ui.security.oauth2

import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import io.circe.{Decoder, Error}
import pl.touk.nussknacker.http.backend.SttpJson
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler.{
  OAuth2AccessTokenRejection,
  OAuth2CompoundException,
  OAuth2ServerError
}
import sttp.client3.{basicRequest, Response, ResponseException, SttpBackend}
import sttp.client3.circe._
import sttp.model.{MediaType, Uri}

import scala.concurrent.{ExecutionContext, Future}

class OAuth2ClientApi[ProfileResponse: Decoder, AccessTokenResponse: Decoder](configuration: OAuth2Configuration)(
    implicit ec: ExecutionContext,
    backend: SttpBackend[Future, Any]
) extends LazyLogging {
  import io.circe.syntax._

  def accessTokenRequest(authorizationCode: String, redirectUri: String): Future[AccessTokenResponse] = {
    val payload: Map[String, String] = Map(
      "client_id"     -> configuration.clientId,
      "client_secret" -> configuration.clientSecret,
      "code"          -> authorizationCode,
      "redirect_uri"  -> redirectUri
    ) ++ configuration.accessTokenParams

    var request =
      basicRequest
        .contentType(configuration.accessTokenRequestContentType)
        .response(asJson[AccessTokenResponse])
        .post(Uri(configuration.accessTokenUri))
        .headers(configuration.headers)

    val contentType = MediaType.parse(configuration.accessTokenRequestContentType)
    request = contentType match {
      case Right(MediaType.ApplicationJson)               => request.body(payload.asJson)
      case Right(MediaType.ApplicationXWwwFormUrlencoded) => request.body(payload)
      case _ =>
        throw OAuth2CompoundException(
          NonEmptyList.of(OAuth2ServerError(s"Unsupported content-type ${configuration.accessTokenRequestContentType}"))
        )
    }

    request
      .send(backend)
      .flatMap(handlingResponse[AccessTokenResponse](_, s"Cannot authorize user by data: $payload."))
      .flatMap(SttpJson.failureToFuture)
  }

  def profileRequest(accessToken: String): Future[ProfileResponse] = {
    val headers = configuration.headers ++ Map(configuration.authorizationHeader -> s"Bearer $accessToken")

    basicRequest
      .response(asJson[ProfileResponse])
      .get(Uri(configuration.profileUri))
      .headers(headers)
      .send(backend)
      .flatMap(handlingResponse[ProfileResponse](_, s"Cannot authenticate user by token: $accessToken."))
      .flatMap(SttpJson.failureToFuture)
  }

  protected[security] def handlingResponse[T](
      response: Response[Either[ResponseException[String, Error], T]],
      clientErrorMessage: String
  ): Future[Response[Either[ResponseException[String, Error], T]]] = {
    if (response.code.isClientError) {
      logger.debug(s"Handling ClientError response: ${response}, error: ${clientErrorMessage}")
      Future.failed(throw OAuth2CompoundException(NonEmptyList.of(OAuth2AccessTokenRejection(clientErrorMessage))))
    } else if (response.isSuccess) {
      Future.successful(response)
    } else {
      logger.debug(s"Handling ServerError response: ${response}")
      Future.failed(
        throw OAuth2CompoundException(NonEmptyList.of(OAuth2ServerError(s"OAuth2 Server error: ${response}")))
      )
    }
  }

}

object OAuth2ClientApi {

  def apply[ProfileResponse: Decoder, AccessTokenResponse: Decoder](configuration: OAuth2Configuration)(
      implicit ec: ExecutionContext,
      sttpBackend: SttpBackend[Future, Any]
  ): OAuth2ClientApi[ProfileResponse, AccessTokenResponse] =
    new OAuth2ClientApi[ProfileResponse, AccessTokenResponse](configuration)

}
