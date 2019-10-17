package pl.touk.nussknacker.ui.api

import akka.http.javadsl.model.headers.Location
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCode, StatusCodes, Uri}
import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.ui.security.AuthenticationConfiguration
import pl.touk.nussknacker.ui.security.oauth2.{OAuth2Configuration, OAuth2Service}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class AuthenticationResources(authenticationConfig: AuthenticationConfiguration)(implicit ec: ExecutionContext)
  extends Directives with FailFastCirceSupport with RouteWithoutUser {

  def route(): Route = pathPrefix("authentication") {
    path("oauth2") {
      parameters('code) { authorizeToken =>
        extractUri { uri =>
          get {
            complete {
              authenticationConfig match {
                case oauth2Configuration: OAuth2Configuration => oAuth2Authenticate(oauth2Configuration, uri, authorizeToken)
                case _ => rejectRequest("Unsupported backend authentication type. Required backend: OAuth2.")
              }
            }
          }
        }
      }
    }
  }

  private def oAuth2Authenticate(oauth2Configuration: OAuth2Configuration, uri: Uri, authorizeToken: String) = {
    val service = new OAuth2Service(oauth2Configuration)
    service.getAccessToken(authorizeToken) match {
      case Success(accessToken) => handleOAuth2Authentication(uri, accessToken)
      case Failure(e) => rejectRequest(e.getMessage)
    }
  }

  private def handleOAuth2Authentication(uri: Uri, accessToken: String) = {
    doRedirect(
      dispatch.url(Uri(scheme=uri.scheme, authority=uri.authority).toString())
        .setQueryParameters(Map("accessToken" -> accessToken).mapValues(v => Seq(v)))
        .url,
      StatusCodes.PermanentRedirect
    )
  }

  private def doRedirect(uri: String, status: StatusCode): Future[ToResponseMarshallable] =
    Future.successful(HttpResponse(status = status).addHeader(Location.create(uri)))

  private def rejectRequest(message: String): Future[ToResponseMarshallable] =
    Future.successful(HttpResponse(status = StatusCodes.Forbidden, entity = message))
}