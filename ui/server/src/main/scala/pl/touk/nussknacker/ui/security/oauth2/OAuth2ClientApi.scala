package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import io.circe.generic.JsonCodec
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import pl.touk.nussknacker.engine.sttp.SttpJson
import sttp.client._
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.circe._
import sttp.model.Uri

import scala.concurrent.{ExecutionContext, Future}
import scala.util.parsing.json.JSONObject

trait AccessTokenResponseDefinition {
  def getAccessToken(): String
  def getTokenType(): String
}

@JsonCodec case class DefaultAccessTokenResponse(access_token: String, token_type: String, refresh_token: Option[String]) extends AccessTokenResponseDefinition {
  override def getAccessToken(): String = access_token
  override def getTokenType(): String = token_type
}

@JsonCodec case class DefaultProfileResponse(id: String, email: String)

trait ServiceOAuth2[ProfileResponse, AccessTokenResponse <: AccessTokenResponseDefinition] {
  def doAccessTokenRequest(authorizeToken: String): Future[AccessTokenResponse]
  def doProfileRequest(authorizeToken: String): Future[ProfileResponse]
}

class OAuth2ClientApi[ProfileResponse: Decoder, AccessTokenResponse <: AccessTokenResponseDefinition: Decoder]
(configuration: OAuth2Configuration)(implicit backend: SttpBackend[Future, Nothing, NothingT])
  extends LazyLogging with ServiceOAuth2[ProfileResponse, AccessTokenResponse] {

  val requestContentType = "application/json"

  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  def doAccessTokenRequest(authorizeToken: String): Future[AccessTokenResponse] = {
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

  def doProfileRequest(accessToken: String): Future[ProfileResponse] = {
    val headers = configuration.headers ++ Map(configuration.authorizationHeader -> s"Bearer $accessToken")

    basicRequest
      .contentType(requestContentType)
      .response(asJson[ProfileResponse])
      .get(Uri(configuration.profileUri))
      .headers(headers)
      .send()
      .flatMap(SttpJson.failureToFuture)
  }
}

object OAuth2ClientApi {
  private implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())
  def apply[ProfileResponse: Decoder, AccessTokenResponse <: AccessTokenResponseDefinition: Decoder](configuration: OAuth2Configuration): OAuth2ClientApi[ProfileResponse, AccessTokenResponse]
    = new OAuth2ClientApi[ProfileResponse, AccessTokenResponse](configuration)
}