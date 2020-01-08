package pl.touk.nussknacker.ui.security.oauth2

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Encoder
import io.circe.generic.JsonCodec

import scala.concurrent.{ExecutionContext, Future}

class AuthenticationOAuth2Resources(service: OAuth2Service)(implicit ec: ExecutionContext)
  extends Directives with LazyLogging with FailFastCirceSupport {

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