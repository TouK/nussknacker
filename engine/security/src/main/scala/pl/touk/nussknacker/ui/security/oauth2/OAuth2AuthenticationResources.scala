package pl.touk.nussknacker.ui.security.oauth2

import akka.http.scaladsl.marshalling.ToResponseMarshallable
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

class OAuth2AuthenticationResources(realm: String, service: OAuth2Service[AuthenticatedUser, OAuth2AuthorizationData], configuration: OAuth2Configuration)(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT])
  extends AuthenticationResources with Directives with LazyLogging with AnonymousAccess {

  override val name: String = configuration.name

  override val frontendStrategySettings: FrontendStrategySettings = FrontendStrategySettings.OAuth2(
    configuration.authorizeUrl.map(_.toString),
    configuration.authSeverPublicKey.map(CertificatesAndKeys.textualRepresentationOfPublicKey),
    configuration.idTokenNonceVerificationRequired,
    configuration.implicitGrantEnabled
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
      parameters('code) { authorizeToken =>
        get {
          complete {
            oAuth2Authenticate(authorizeToken)
          }
        }
      }
    }

  private def oAuth2Authenticate(authorizationCode: String): Future[ToResponseMarshallable] = {
    service.obtainAuthorizationAndUserInfo(authorizationCode).map { case (auth, _) =>
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