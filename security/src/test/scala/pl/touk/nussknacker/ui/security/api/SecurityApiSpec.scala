package pl.touk.nussknacker.ui.security.api

import java.nio.charset.StandardCharsets
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers._
import org.apache.pekko.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.directives.BasicDirectives.{extractExecutionContext, provide}
import org.apache.pekko.http.scaladsl.server.directives.{AuthenticationDirective, Credentials}
import org.apache.pekko.http.scaladsl.server.directives.FutureDirectives.onSuccess
import org.apache.pekko.http.scaladsl.server.directives.RouteDirectives.reject
import org.apache.pekko.http.scaladsl.server.{AuthenticationFailedRejection, Directive1, Route}
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{ExecutionContext, Future}

class SecurityApiSpec extends org.scalatest.flatspec.AnyFlatSpec with Matchers with ScalatestRouteTest {

  import SecurityApiSpec._

  it should " reject basic auth without credentials" in {
    Get("/secured") ~> route(basic) ~> check {
      status shouldEqual StatusCodes.Unauthorized
      responseAs[String] shouldEqual "The resource requires authentication, which was not supplied with the request"
      header[`WWW-Authenticate`].get.challenges.head shouldEqual HttpChallenge(
        "Basic",
        "nussknacker",
        Map("charset" -> StandardCharsets.UTF_8.name)
      )
    }
  }

  it should "support basic auth" in {
    val validCredentials = BasicHttpCredentials("admin", "admin")
    Get("/secured") ~> addCredentials(validCredentials) ~> route(basic) ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldEqual "Authenticated!"
    }
  }

  it should "reject basic auth invalid credentials" in {
    val invalidCredentials = BasicHttpCredentials("admin", "wrong password")
    Get("/secured") ~> addCredentials(invalidCredentials) ~> route(basic) ~> check {
      status shouldEqual StatusCodes.Unauthorized
      responseAs[String] shouldEqual "The supplied authentication is invalid"
    }
  }

  ignore should "support access any header"

  it should "reject cookie auth without credentials" in {
    Get("/secured") ~> route(cookieAuth) ~> check {
      status shouldEqual StatusCodes.BadRequest
      responseAs[String] shouldEqual "Request is missing required cookie 'authCookieToken'"
    }
  }

  it should "support cookie session" in {
    val cookie = Cookie("authCookieToken", "Im_a_random_hash")
    Get("/secured") ~> addHeader(cookie) ~> route(cookieAuth) ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldEqual "Authenticated!"
    }
  }

  it should "reject invalid cookie session" in {
    val cookie = Cookie("authCookieToken", "Im_ugly_thief")
    Get("/secured") ~> addHeader(cookie) ~> route(cookieAuth) ~> check {
      status shouldEqual StatusCodes.Unauthorized
      responseAs[String] shouldEqual "The supplied authentication is invalid"
    }
  }

}

private object Auth {

  val basic = {
    authenticateBasic(realm = "nussknacker", myUserPassAuthenticator)
  }

  val cookieAuth = {
    cookie("authCookieToken").flatMap { cookie =>
      extractExecutionContext.flatMap { implicit ec =>
        onSuccess(authenticator(cookie)).flatMap {
          case Some(user) => provide(user)
          case None =>
            reject(AuthenticationFailedRejection(CredentialsRejected, HttpChallenge("", ""))): Directive1[
              AuthenticatedUser
            ]
        }
      }
    }
  }

  val someAdmin = Some(AuthenticatedUser("1", "admin", Set.empty))

  def authenticator(cookie: HttpCookiePair)(implicit ec: ExecutionContext): Future[Option[AuthenticatedUser]] = {
    Future {
      cookie match {
        case HttpCookiePair(_, "Im_a_random_hash") => someAdmin
        case _                                     => None
      }
    }
  }

  def myUserPassAuthenticator(credentials: Credentials): Option[AuthenticatedUser] =
    credentials match {
      case p @ Credentials.Provided(id) if p.verify("admin") => someAdmin
      case _                                                 => None
    }

}

private object SecurityApiSpec {
  val cookieAuth = Auth.cookieAuth
  val basic      = Auth.basic

  implicit val actorSystem: ActorSystem = ActorSystem.create()

  def route(authenticator: AuthenticationDirective[AuthenticatedUser]): Route =
    Route.seal {
      path("secured") {
        authenticator { userName =>
          complete("Authenticated!")
        }
      }
    }

}
