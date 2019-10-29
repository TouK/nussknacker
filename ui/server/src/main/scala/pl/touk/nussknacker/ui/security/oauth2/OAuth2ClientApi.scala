package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import io.circe.generic.JsonCodec
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import pl.touk.nussknacker.engine.sttp.SttpJson
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler.{OAuth2AccessTokenRejection, OAuth2AuthenticationRejection}
import sttp.client._
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.circe._
import sttp.model.Uri

import scala.concurrent.{ExecutionContext, Future}

class OAuth2ClientApi[ProfileResponse: Decoder, AccessTokenResponse: Decoder]
(configuration: OAuth2Configuration, requestContentType: String = "application/json")
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

    basicRequest
      .body(payload.asJson)
      .contentType(requestContentType)
      .response(asJson[AccessTokenResponse])
      .post(Uri(configuration.accessTokenUri))
      .headers(configuration.headers)
      .send()
      .flatMap { resp =>
        if (resp.code.isClientError) {
          Future.failed(throw OAuth2AccessTokenRejection(s"Cannot authorize user by data: $payload."))
        } else {
          Future.successful(resp)
        }
      }
      .flatMap(SttpJson.failureToFuture)
  }

  def profileRequest(accessToken: String): Future[ProfileResponse] = {
    val headers = configuration.headers ++ Map(configuration.authorizationHeader -> s"Bearer $accessToken")

    basicRequest
      .contentType(requestContentType)
      .response(asJson[ProfileResponse])
      .get(Uri(configuration.profileUri))
      .headers(headers)
      .send()
      .flatMap { resp =>
        //We have to check is error from server is about problem with authentication
        if (resp.code.isClientError) {
          Future.failed(throw OAuth2AuthenticationRejection(s"Cannot authenticate user by token: $accessToken."))
        } else {
          Future.successful(resp)
        }
      }
      .flatMap(SttpJson.failureToFuture)
  }
}

object OAuth2ClientApi {
  private implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())
  def apply[ProfileResponse: Decoder, AccessTokenResponse: Decoder](configuration: OAuth2Configuration): OAuth2ClientApi[ProfileResponse, AccessTokenResponse]
    = new OAuth2ClientApi[ProfileResponse, AccessTokenResponse](configuration)

  @JsonCodec case class DefaultAccessTokenResponse(access_token: String, token_type: String, refresh_token: Option[String])
  @JsonCodec case class DefaultProfileResponse(id: Int, email: String)
}