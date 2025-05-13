package pl.touk.nussknacker.ui.security.oidc

import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec, JsonKey}
import sttp.client3.{basicRequest, SttpBackend, UriContext}
import sttp.client3.circe.asJson
import sttp.model.MediaType

import java.net.URI
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.{Duration, SECONDS}
import scala.util.Try

object OidcDiscovery extends LazyLogging {
  import pl.touk.nussknacker.engine.api.CirceUtil.codecs._
  implicit val config: Configuration = Configuration.default

  def apply(issuer: URI)(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Any]): Option[OidcDiscovery] =
    Try(
      Await
        .result(
          basicRequest
            .contentType(MediaType.ApplicationJson)
            .get(uri"$issuer/.well-known/openid-configuration")
            .response(asJson[OidcDiscovery])
            .send(sttpBackend),
          Duration(30, SECONDS)
        )
        .body
    ).fold(Left(_), identity) match {
      case Right(v) => Some(v)
      case Left(err) =>
        logger.warn(s"Unable to retrieve the OpenID Provider's configuration: ${err.getMessage}")
        None
    }

}

@ConfiguredJsonCodec
final case class OidcDiscovery(
    issuer: URI,
    @JsonKey("authorization_endpoint") authorizationEndpoint: URI,
    @JsonKey("token_endpoint") tokenEndpoint: URI,
    @JsonKey("userinfo_endpoint") userinfoEndpoint: URI,
    @JsonKey("jwks_uri") jwksUri: URI,
    @JsonKey("supported_scopes") scopesSupported: Option[List[String]],
    @JsonKey("response_types_supported") responseTypesSupported: List[String]
)
