package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server.{Directives, Route}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.ui.security.oauth2.OAuth2Service

import scala.concurrent.{ExecutionContext, Future}

class AuthenticationOAuth2Resources(service: OAuth2Service)(implicit ec: ExecutionContext)
  extends Directives with FailFastCirceSupport with RouteWithoutUser with LazyLogging {

  def route(): Route = pathPrefix("authentication") {
    path("oauth2") {
      parameters('code) { authorizeToken =>
        get {
          complete {
            oAuth2Authenticate(authorizeToken)
          }
        }
      }
    }
  }

  private def oAuth2Authenticate(authorizeToken: String): Future[ToResponseMarshallable] = {
    service.authenticate(authorizeToken).map { auth =>
      ToResponseMarshallable(Oauth2AuthenticationResponse(auth.access_token, auth.token_type))
    }.recover {
      case ex =>
        logger.warn("Error at retrieving access token:", ex)
        EspErrorToHttp.toResponseReject("Retrieving access token error. Please contact with system administrators.")
    }
  }
}

@JsonCodec case class Oauth2AuthenticationResponse(accessToken: String, tokenType: String)