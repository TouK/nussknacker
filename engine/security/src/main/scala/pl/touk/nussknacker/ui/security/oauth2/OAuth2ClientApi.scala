package pl.touk.nussknacker.ui.security.oauth2

import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import io.circe.{Decoder, Error}
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import pl.touk.nussknacker.engine.sttp.SttpJson
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler.{OAuth2AccessTokenRejection, OAuth2CompoundException, OAuth2ServerError}
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.circe._
import sttp.client.{Response, _}
import sttp.model.{MediaType, Uri}

import scala.concurrent.{ExecutionContext, Future}

class OAuth2ClientApi[ProfileResponse: Decoder, AccessTokenResponse: Decoder]
(configuration: OAuth2Configuration)
(implicit backend: SttpBackend[Future, Nothing, NothingT]) extends LazyLogging {
  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  import io.circe.syntax._

  def accessTokenRequest(authorizeToken: String): Future[AccessTokenResponse] = {
    val payload: Map[String, String] = Map(
      "client_id" -> configuration.clientId,
      "client_secret" -> configuration.clientSecret,
      "code" -> authorizeToken,
      "redirect_uri" -> configuration.redirectUrl
    ) ++ configuration.accessTokenParams

    var request =
      basicRequest
      .contentType(configuration.accessTokenRequestContentType)
      .response(asJson[AccessTokenResponse])
      .post(Uri(configuration.accessTokenUri))
      .headers(configuration.headers)

    val contentType = MediaType.parse(configuration.accessTokenRequestContentType)
    request = contentType match {
      case Right(MediaType.ApplicationJson) => request.body(payload.asJson)
      case _ => request.body(payload)
    }

    request
      .send()
      .flatMap(handlingResponse[AccessTokenResponse](_, s"Cannot authorize user by data: $payload."))
      .flatMap(SttpJson.failureToFuture)
  }

  def profileRequest(accessToken: String): Future[ProfileResponse] = {
    val headers = configuration.headers ++ Map(configuration.authorizationHeader -> s"Bearer $accessToken")

    basicRequest
      .contentType(configuration.profileResponseContentType)
      .response(asJson[ProfileResponse])
      .get(Uri(configuration.profileUri))
      .headers(headers)
      .send()
      .flatMap(handlingResponse[ProfileResponse](_, s"Cannot authenticate user by token: $accessToken."))
      .flatMap(SttpJson.failureToFuture)
  }

  protected[security] def handlingResponse[T](response: Response[Either[ResponseError[Error], T]], clientErrorMessage: String): Future[Response[Either[ResponseError[Error], T]]] = {
    if (response.code.isClientError) {
      logger.debug(s"Handling ClientError response: ${response}, error: ${clientErrorMessage}")
      Future.failed(throw OAuth2CompoundException(NonEmptyList.of(OAuth2AccessTokenRejection(clientErrorMessage))))
    } else if (response.isSuccess) {
      Future.successful(response)
    } else {
      logger.debug(s"Handling ServerError response: ${response}")
      Future.failed(throw OAuth2CompoundException(NonEmptyList.of(OAuth2ServerError(s"OAuth2 Server error: ${response}"))))
    }
  }
}

object OAuth2ClientApi {
  implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())
  def apply[ProfileResponse: Decoder, AccessTokenResponse: Decoder](configuration: OAuth2Configuration): OAuth2ClientApi[ProfileResponse, AccessTokenResponse]
    = new OAuth2ClientApi[ProfileResponse, AccessTokenResponse](configuration)

  @JsonCodec case class DefaultAccessTokenResponse(access_token: String, token_type: String, refresh_token: Option[String])
}
