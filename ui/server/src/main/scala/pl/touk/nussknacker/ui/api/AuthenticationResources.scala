package pl.touk.nussknacker.ui.api

import akka.http.javadsl.model.headers.Location
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCode, StatusCodes, Uri}
import akka.http.scaladsl.server.{Directives, Route}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.ui.security.AuthenticationConfiguration
import pl.touk.nussknacker.ui.security.oauth2.{OAuth2Configuration, OAuth2Service}

import scala.concurrent.ExecutionContext

class AuthenticationResources(authenticationConfig: AuthenticationConfiguration)(implicit ec: ExecutionContext)
  extends Directives with FailFastCirceSupport with RouteWithoutUser with LazyLogging {

  def route(): Route = pathPrefix("authentication") {
    path("oauth2") {
      parameters('code) { authorizeToken =>
        extractUri { uri =>
          get {
            complete {
              authenticationConfig match {
                case oauth2Configuration: OAuth2Configuration => oAuth2Authenticate(oauth2Configuration, uri, authorizeToken)
                case _ => EspErrorToHttp.toResponseReject("Unsupported backend authentication type. Required backend: OAuth2.")
              }
            }
          }
        }
      }
    }
  }

  private def oAuth2Authenticate(oauth2Configuration: OAuth2Configuration, uri: Uri, authorizeToken: String)= {
    val service = new OAuth2Service(oauth2Configuration)
    service.accessTokenRequest(authorizeToken).map { response =>
      handleOAuth2Authentication(uri, response.getAccessToken())
    }.recover {
      case ex =>
        logger.warn("Error at retrieving access token:", ex)
        EspErrorToHttp.toResponseReject("Retrieving access token error. Please contact with system administrator.")
    }
  }

  private def handleOAuth2Authentication(uri: Uri, accessToken: String): ToResponseMarshallable =
    doRedirect(
      dispatch.url(Uri(scheme=uri.scheme, authority=uri.authority).toString())
        .setQueryParameters(Map("accessToken" -> accessToken).mapValues(v => Seq(v)))
        .url,
      StatusCodes.PermanentRedirect
    )

  private def doRedirect(uri: String, status: StatusCode): ToResponseMarshallable =
    HttpResponse(status = status).addHeader(Location.create(uri))
}