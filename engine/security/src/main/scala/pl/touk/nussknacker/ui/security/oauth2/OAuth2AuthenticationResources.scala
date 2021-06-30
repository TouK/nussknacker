package pl.touk.nussknacker.ui.security.oauth2

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.http.scaladsl.server.{Directives, Route}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Encoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.ui.security.CertificatesAndKeys
import pl.touk.nussknacker.ui.security.api.AuthenticationResources.LoggedUserAuth
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

class OAuth2AuthenticationResources(realm: String, service: OAuth2Service[LoggedUser, OAuth2AuthorizationData], configuration: OAuth2Configuration)(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT])
  extends AuthenticationResources with Directives with LazyLogging with FailFastCirceSupport {

  override val name: String = configuration.method.toString

  override val frontendSettings: ToResponseMarshallable = OAuth2AuthenticationSettings(
    configuration.authorizeUrl.map(_.toString),
    configuration.authSeverPublicKey.map(CertificatesAndKeys.textualRepresentationOfPublicKey),
    configuration.idTokenNonceVerificationRequired,
    configuration.implicitGrantEnabled
  )

  override def authenticate(): LoggedUserAuth = {
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

@JsonCodec case class OAuth2AuthenticationSettings(authorizeUrl: Option[String],
                                                   jwtAuthServerPublicKey: Option[String],
                                                   jwtIdTokenNonceVerificationRequired: Boolean,
                                                   implicitGrantEnabled: Boolean)