package pl.touk.nussknacker.ui.security.oidc

import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec, JsonKey}
import net.ceedubs.ficus.readers.URIReaders
import sttp.client.circe.asJson
import sttp.client.{NothingT, SttpBackend, UriContext, basicRequest}
import sttp.model.MediaType

import java.net.URI
import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.{Await, ExecutionContext, Future}

object OidcDiscovery extends LazyLogging {

  implicit val uriDecoder: Decoder[URI] = Decoder.decodeString.map(URI.create)
  implicit val config: Configuration = Configuration.default

  def apply(issuer: URI)(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): Option[OidcDiscovery] =
    Await.result(
      basicRequest
        .contentType(MediaType.ApplicationJson)
        .get(uri"$issuer/.well-known/openid-configuration")
        .response(asJson[OidcDiscovery]).send(),
      Duration(30, SECONDS)
    ).body match {
      case Right(v) => Some(v)
      case Left(err) =>
        logger.warn(s"Unable to retrieve the OpenID Provider's configuration: ${err.getMessage}")
        None
    }
}

@ConfiguredJsonCodec(decodeOnly = true)
case class OidcDiscovery
(
  issuer: URI,
  @JsonKey("authorization_endpoint") authorizationEndpoint: URI,
  @JsonKey("token_endpoint") tokenEndpoint: URI,
  @JsonKey("userinfo_endpoint") userinfoEndpoint: URI,
  @JsonKey("jwks_uri") jwksUri: URI,
  @JsonKey("supported_scopes") scopesSupported: Option[List[String]],
  @JsonKey("response_types_supported") responseTypesSupported: List[String]
)