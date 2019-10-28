package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import io.circe.generic.JsonCodec
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import pl.touk.nussknacker.engine.sttp.SttpJson
import pl.touk.nussknacker.ui.security.oauth2.OAuth2Authenticator.OAuth2TokenRejection
import sttp.client._
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.circe._
import sttp.model.{StatusCode, Uri}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.parsing.json.JSONObject

class OAuth2ClientApi[ProfileResponse: Decoder, AccessTokenResponse: Decoder]
(configuration: OAuth2Configuration, requestContentType: String = "application/json")
(implicit backend: SttpBackend[Future, Nothing, NothingT]) extends LazyLogging {
  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  def accessTokenRequest(authorizeToken: String): Future[AccessTokenResponse] = {
    val payload: Map[String, String] = Map(
      "client_id" -> configuration.clientId,
      "client_secret" -> configuration.clientSecret,
      "code" -> authorizeToken,
      "redirect_uri" -> configuration.redirectUrl
    ) ++ configuration.accessTokenParams

    basicRequest
      .body(JSONObject(payload).toString().stripMargin)
      .contentType(requestContentType)
      .response(asJson[AccessTokenResponse])
      .post(Uri(configuration.accessTokenUri))
      .headers(configuration.headers)
      .send()
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
        if (resp.code.equals(StatusCode.Unauthorized)) { //We have to check is error from server is about problem with authentication
          Future.failed(throw OAuth2TokenRejection(s"Cannot authenticate user by token: $accessToken."))
        }

        Future.successful(resp)
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