package pl.touk.nussknacker.ui.security.api

import org.apache.pekko.http.javadsl.model.headers.HttpCredentials
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.server.{Directives, Route}
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.ui.security.basicauth.BasicAuthenticationResources
import sttp.client3.testing.SttpBackendStub

import scala.concurrent.Future

class AuthManagerSpec extends AnyFunSpec with Matchers with ScalatestRouteTest with Directives {

  implicit private val testingBackend: SttpBackendStub[Future, Any] = SttpBackendStub.asynchronousFuture
  private val classLoader                                           = getClass.getClassLoader

  private val username             = "admin"
  private val anonymousUsername    = "anonymous"
  private val impersonatedUsername = "user"

  private val config = ConfigFactory.parseString(s"""
        authentication: {
          method: "BasicAuth"
          anonymousUserRole: "Anonymous"
          usersFile: "classpath:basic-users.conf"
        }
      """.stripMargin)

  private val authenticationResources = {
    val resources = AuthenticationResources(config, classLoader, testingBackend)
    assert(resources.isInstanceOf[BasicAuthenticationResources])
    resources
  }

  private val authManager = new AuthManager(authenticationResources)

  private val testRoute = Route.seal(
    authManager.authenticate() { authenticatedUser =>
      val endAuthenticatedUser = authenticatedUser.impersonatedAuthenticationUser match {
        case Some(impersonatedUser) => impersonatedUser
        case None                   => authenticatedUser
      }
      path("public") {
        get {
          complete(endAuthenticatedUser.username)
        }
      } ~ path("processes") {
        authorize(endAuthenticatedUser.roles.contains("Admin")) {
          get {
            complete(endAuthenticatedUser.username)
          }
        }
      }
    }
  )

  it("should authenticate an anonymous user when requesting an unrestricted resource") {
    Get("/public") ~> testRoute ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldEqual s"$anonymousUsername"
    }
  }

  it("should not authenticate an anonymous user when requesting a restricted resource") {
    Get("/processes") ~> testRoute ~> check {
      status shouldEqual StatusCodes.Unauthorized
    }
  }

  it("should authenticate user with passed credentials") {
    Get("/processes").addCredentials(
      HttpCredentials.createBasicHttpCredentials(username, username)
    ) ~> testRoute ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldEqual s"$username"
    }
  }

  it("should not authenticate user with invalid credentials") {
    Get("/processes").addCredentials(
      HttpCredentials.createBasicHttpCredentials(username, "wrong")
    ) ~> testRoute ~> check {
      status shouldEqual StatusCodes.Unauthorized
    }
  }

  it("should impersonate when provided with credentials and the header") {
    Get("/public")
      .addCredentials(HttpCredentials.createBasicHttpCredentials(username, username))
      .addHeader(
        RawHeader(
          AuthManager.impersonateHeaderName,
          impersonatedUsername
        )
      ) ~> testRoute ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldEqual s"$impersonatedUsername"
    }
  }

  it("should not impersonate when provided with invalid credentials and the header") {
    Get("/public")
      .addCredentials(HttpCredentials.createBasicHttpCredentials(username, "wrong"))
      .addHeader(
        RawHeader(
          AuthManager.impersonateHeaderName,
          impersonatedUsername
        )
      ) ~> testRoute ~> check {
      status shouldEqual StatusCodes.Unauthorized
    }
  }

  it("should not authenticate an anonymous user when requesting a restricted resource with impersonation") {
    Get("/processes")
      .addHeader(
        RawHeader(
          AuthManager.impersonateHeaderName,
          impersonatedUsername
        )
      ) ~> testRoute ~> check {
      status shouldEqual StatusCodes.Unauthorized
    }
  }

}
