package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import pl.touk.nussknacker.engine.sttp.SttpJson
import sttp.client._
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.circe._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.parsing.json.JSONObject

trait AccessTokenResponse {
  def getAccessToken(): String
}

@JsonCodec case class BaseAccessTokenResponse(access_token: String, token_type: String, refresh_token: String) extends AccessTokenResponse {
  override def getAccessToken(): String = access_token
}

trait ServiceOAuth2 {
  def accessTokenRequest(authorizeToken: String): Future[AccessTokenResponse]
}

class OAuth2Service(configuration: OAuth2Configuration)(implicit backend: SttpBackend[Future, Nothing, NothingT]) extends LazyLogging with ServiceOAuth2 {
  val requestContentType = "application/json"

  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  def accessTokenRequest(authorizeToken: String): Future[AccessTokenResponse] = {
    val payload: Map[String, String] = Map(
      "client_id" -> configuration.clientId,
      "client_secret" -> configuration.clientSecret,
      "code" -> authorizeToken,
      "redirect_uri" -> configuration.getRedirectUrl()
    ) ++ configuration.accessTokenParams

    basicRequest
      .body(JSONObject(payload).toString().stripMargin)
      .contentType(requestContentType)
      .response(asJson[BaseAccessTokenResponse])
      .post(configuration.getAccessTokenSttpUri)
      .headers(configuration.headers)
      .send()
      .flatMap(SttpJson.failureToFuture)
  }
}

object OAuth2Service {
  private implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())
  def apply(configuration: OAuth2Configuration): OAuth2Service = new OAuth2Service(configuration)
}