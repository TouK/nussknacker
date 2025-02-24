package pl.touk.nussknacker.ui.security.basicauth

import org.apache.pekko.http.javadsl.model.headers.HttpCredentials
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.{Directives, Route}
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.ui.security.api.AuthenticationResources
import sttp.client3.testing.SttpBackendStub

import scala.concurrent.Future

class BasicAuthenticationSpec extends AnyFunSpec with Matchers with ScalatestRouteTest with Directives {

  implicit private val testingBackend: SttpBackendStub[Future, Any] = SttpBackendStub.asynchronousFuture
  private val classLoader                                           = getClass.getClassLoader

  private val anonymousUserRole = "Anonymous"

  private val config = ConfigFactory.parseString(
    s"""
       |authentication: {
       |  method: "BasicAuth"
       |  anonymousUserRole: "${anonymousUserRole}"
       |  usersFile: "classpath:basic-users.conf",
       |  realm: "nussknacker-test"
       |}
       |""".stripMargin
  )

  private val authenticationResources = AuthenticationResources(config, classLoader, testingBackend)
  assert(authenticationResources.isInstanceOf[BasicAuthenticationResources])

  private val testRoute = Route.seal(
    authenticationResources.authenticate() { authenticatedUser =>
      path("public") {
        get {
          complete(authenticatedUser.roles.mkString)
        }
      } ~ path("config") {
        authorize(authenticatedUser.roles.contains("Admin")) {
          get {
            complete(authenticatedUser.roles.mkString)
          }
        }
      }
    }
  )

  it("should permit an authenticated user") {
    Get("/public").addCredentials(HttpCredentials.createBasicHttpCredentials("user", "user")) ~> testRoute ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldEqual "User"
    }
  }

  it("should request authorization on an access without credentials to a restricted resource") {
    Get("/config") ~> testRoute ~> check {
      status shouldEqual StatusCodes.Unauthorized
    }
  }

  it("should request authorization on invalid credentials") {
    Get("/public").addCredentials(HttpCredentials.createBasicHttpCredentials("user", "invalid")) ~> testRoute ~> check {
      status shouldEqual StatusCodes.Unauthorized
    }
  }

  it("should permit an authorized user to a restricted resource") {
    Get("/config").addCredentials(HttpCredentials.createBasicHttpCredentials("admin", "admin")) ~> testRoute ~> check {
      status shouldEqual StatusCodes.OK
    }
  }

  it("should deny an authenticated but unauthorized user to a restricted resource") {
    Get("/config").addCredentials(HttpCredentials.createBasicHttpCredentials("user", "user")) ~> testRoute ~> check {
      status shouldEqual StatusCodes.Forbidden
    }
  }

  it("should use custom realm") {
    Get("/public") ~> testRoute ~> check {
      status shouldEqual StatusCodes.Unauthorized
      header("WWW-Authenticate").map(_.value()) shouldEqual Some("Basic realm=\"nussknacker-test\",charset=UTF-8")
    }
  }

}
