package pl.touk.nussknacker.ui.security.oauth2

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes.NotFound
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import akka.http.scaladsl.server.{Directives, Route}
import com.typesafe.scalalogging.LazyLogging
import io.circe.Encoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.ui.security.CertificatesAndKeys
import pl.touk.nussknacker.ui.security.api.{AnonymousAccess, AuthenticatedUser, AuthenticationResources, FrontendStrategySettings}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

class OAuth2AuthenticationResources(override val name: String, realm: String, service: OAuth2Service[AuthenticatedUser, OAuth2AuthorizationData], configuration: OAuth2Configuration)(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT])
  extends AuthenticationResources with Directives with LazyLogging with AnonymousAccess {

  import pl.touk.nussknacker.engine.util.Implicits.RichIterable

  override val frontendStrategySettings: FrontendStrategySettings = FrontendStrategySettings.OAuth2(
    configuration.authorizeUrl.map(_.toString),
    configuration.authSeverPublicKey.map(CertificatesAndKeys.textualRepresentationOfPublicKey),
    configuration.idTokenNonceVerificationRequired,
    configuration.implicitGrantEnabled,
    configuration.anonymousUserRole.isDefined
  )

  val anonymousUserRole: Option[String] = configuration.anonymousUserRole

  override def authenticateReally(): AuthenticationDirective[AuthenticatedUser] = {
    SecurityDirectives.authenticateOAuth2Async(
      authenticator = OAuth2Authenticator(configuration, service),
      realm = realm
    )
  }

  override lazy val additionalRoute: Route =
    pathEnd {
      parameters('code,  'redirect_uri.?) { (authorizationCode, redirectUri) =>
        get {
          Seq(redirectUri, configuration.redirectUri.map(_.toString)).flatten.exactlyOne.map { redirectUri =>
            complete {
              oAuth2Authenticate(authorizationCode, redirectUri)
            }
          }.getOrElse {
            complete((NotFound, "Redirect URI must be provided either in configuration or in query params"))
          }
        }
      }
    }

  private def oAuth2Authenticate(authorizationCode: String, redirectUri: String): Future[ToResponseMarshallable] = {
    service.obtainAuthorizationAndUserInfo(authorizationCode, redirectUri).map { case (auth, _) =>
      ToResponseMarshallable(Oauth2AuthenticationResponse(auth.accessToken, auth.tokenType))
    }.recover {
      case OAuth2ErrorHandler(ex) => {
        logger.debug("Retrieving access token error:", ex)
        toResponseReject(Map("message" -> "Retrieving access token error. Please try authenticate again."))
      }
    }
  }

  private def toResponseReject(entity: Map[String, String]): ToResponseMarshallable = {
    HttpResponse(
      status = StatusCodes.BadRequest,
      entity = HttpEntity(ContentTypes.`application/json`, Encoder.encodeMap[String, String].apply(entity).spaces2)
    )
  }
}

@JsonCodec case class Oauth2AuthenticationResponse(accessToken: String, tokenType: String)