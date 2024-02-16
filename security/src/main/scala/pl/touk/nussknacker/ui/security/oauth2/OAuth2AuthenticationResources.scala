package pl.touk.nussknacker.ui.security.oauth2

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes.NotFound
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{HttpCookie, `Set-Cookie`}
import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import akka.http.scaladsl.server.{Directives, Route}
import com.typesafe.scalalogging.LazyLogging
import io.circe.Encoder
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.security.AuthCredentials.PassedAuthCredentials
import pl.touk.nussknacker.ui.security.CertificatesAndKeys
import pl.touk.nussknacker.ui.security.api._
import sttp.client3.SttpBackend
import sttp.model.HeaderNames
import sttp.model.headers.{AuthenticationScheme, WWWAuthenticateChallenge}
import sttp.tapir.EndpointInput.AuthType
import sttp.tapir._

import scala.collection.immutable.ListMap
import scala.concurrent.{ExecutionContext, Future}

class OAuth2AuthenticationResources(
    override val name: String,
    realm: String,
    service: OAuth2Service[AuthenticatedUser, OAuth2AuthorizationData],
    override val configuration: OAuth2Configuration
)(implicit executionContext: ExecutionContext, sttpBackend: SttpBackend[Future, Any])
    extends AuthenticationResources
    with Directives
    with LazyLogging
    with AnonymousAccess {

  import pl.touk.nussknacker.engine.util.Implicits.RichIterable

  override type CONFIG = OAuth2Configuration

  private val authenticator = OAuth2Authenticator(service)

  override protected def authenticateReally(): AuthenticationDirective[AuthenticatedUser] = {
    SecurityDirectives.authenticateOAuth2Async(
      authenticator = authenticator,
      realm = realm
    )
  }

  override protected def authenticateReally(credentials: PassedAuthCredentials): Future[Option[AuthenticatedUser]] = {
    authenticator.authenticate(credentials.value)
  }

  override protected def rawAuthCredentialsMethod: EndpointInput[Option[String]] = {
    EndpointInput
      .Auth[Option[String], AuthType.OAuth2](
        header[Option[String]](HeaderNames.Authorization).map(stringPrefixWithSpace(AuthenticationScheme.Bearer.name)),
        WWWAuthenticateChallenge.bearer(realm),
        EndpointInput.AuthType.OAuth2(
          authorizationUrl = configuration.authorizeUrl.map(_.toString),
          // it's only for OpenAPI UI purpose to be able to use "Try It Out" feature. UI calls authorization URL
          // (e.g. Github) and then calls our proxy for Bearer token. It uses the received token while calling the NU API
          tokenUrl = Some(s"../authentication/${name.toLowerCase()}"),
          scopes = ListMap.empty,
          refreshUrl = None
        ),
        EndpointInput.AuthInfo.Empty
      )
  }

  override protected val frontendStrategySettings: FrontendStrategySettings =
    configuration.overrideFrontendAuthenticationStrategy.getOrElse(
      FrontendStrategySettings.OAuth2(
        configuration.authorizeUrl.map(_.toString),
        configuration.authSeverPublicKey.map(CertificatesAndKeys.textualRepresentationOfPublicKey),
        configuration.idTokenNonceVerificationRequired,
        configuration.implicitGrantEnabled,
        configuration.anonymousUserRole.isDefined
      )
    )

  override protected lazy val additionalRoute: Route =
    pathEnd {
      parameters(Symbol("code"), Symbol("redirect_uri").?) { (authorizationCode, redirectUri) =>
        get {
          completeOAuth2Authenticate(authorizationCode, redirectUri)
        }
      } ~
        formFields(Symbol("code"), Symbol("redirect_uri").?) { (authorizationCode, redirectUri) =>
          (get | post) {
            completeOAuth2Authenticate(authorizationCode, redirectUri)
          }
        }
    }

  private def completeOAuth2Authenticate(authorizationCode: String, redirectUri: Option[String]) = {
    determineRedirectUri(redirectUri) match {
      case Some(redirectUri) =>
        complete {
          oAuth2Authenticate(authorizationCode, redirectUri)
        }
      case None =>
        complete((NotFound, "Redirect URI must be provided either in configuration or in query params"))
    }
  }

  private def determineRedirectUri(redirectUriFromRequest: Option[String]): Option[String] = {
    val redirectUriFromConfiguration = configuration.redirectUri.map(_.toString)
    redirectUriFromRequest match {
      // when the redirect URI is the Swagger UI one, force to use the URI
      case Some(uri) if uri.endsWith("/docs/oauth2-redirect.html") =>
        Some(uri)
      case _ =>
        List(redirectUriFromRequest, redirectUriFromConfiguration).flatten.exactlyOne
    }
  }

  private def oAuth2Authenticate(authorizationCode: String, redirectUri: String): Future[ToResponseMarshallable] = {
    service
      .obtainAuthorizationAndAuthenticateUser(authorizationCode, redirectUri)
      .map { case (auth, _) =>
        val response = Oauth2AuthenticationResponse(auth.accessToken, auth.tokenType)
        val cookieHeader = configuration.tokenCookie
          .map { config =>
            `Set-Cookie`(
              HttpCookie(
                config.name,
                auth.accessToken,
                httpOnly = true,
                secure =
                  true, // consider making it configurable (with default value 'true') so one could setup development environment easier (by setting it to 'false')
                path = config.path,
                domain = config.domain,
                maxAge = auth.expirationPeriod.map(_.toSeconds),
              )
            )
          }
        ToResponseMarshallable(
          HttpResponse(
            entity = HttpEntity(ContentTypes.`application/json`, response.asJson.noSpaces),
            headers = cookieHeader.toList
          )
        )
      }
      .recover { case OAuth2ErrorHandler(ex) =>
        logger.debug("Retrieving access token error:", ex)
        toResponseReject(Map("message" -> "Retrieving access token error. Please try authenticate again."))
      }
  }

  private def toResponseReject(entity: Map[String, String]): ToResponseMarshallable = {
    HttpResponse(
      status = StatusCodes.BadRequest,
      entity = HttpEntity(ContentTypes.`application/json`, Encoder.encodeMap[String, String].apply(entity).spaces2)
    )
  }

  private def stringPrefixWithSpace(prefix: String): Mapping[Option[String], Option[String]] =
    Mapping
      .fromDecode[Option[String], Option[String]] {
        case Some(value) => removePrefix(value, prefix).map(Some(_))
        case None        => DecodeResult.Value(None)
      } {
        _.map(value => s"$prefix$value")
      }

  private def removePrefix(value: String, prefix: String) = {
    if (value.toLowerCase.startsWith(prefix.toLowerCase)) {
      DecodeResult.Value(value.substring(prefix.length))
    } else {
      DecodeResult.Error(
        value,
        new IllegalArgumentException(s"Cannot remove $prefix, because the value doesn't start with it")
      )
    }
  }

}

final case class Oauth2AuthenticationResponse(accessToken: String, tokenType: String)

object Oauth2AuthenticationResponse {

  implicit val encoder: Encoder[Oauth2AuthenticationResponse] = Encoder
    // Swagger UI uses snake case instead of camel case, so we produce the response in these two cases
    .forProduct4("accessToken", "tokenType", "access_token", "token_type")(r =>
      (r.accessToken, r.tokenType, r.accessToken, r.tokenType)
    )

}
