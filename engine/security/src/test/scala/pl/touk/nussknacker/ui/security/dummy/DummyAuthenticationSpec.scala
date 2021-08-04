package pl.touk.nussknacker.ui.security.dummy

import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSpec, Matchers}
import pl.touk.nussknacker.ui.security.api.{AuthenticationProvider, AuthenticationResources}
import sttp.client.{Identity, SttpBackend}
import sttp.client.testing.SttpBackendStub

import scala.concurrent.Future

class DummyAuthenticationSpec extends FunSpec with Matchers with ScalatestRouteTest with Directives {

  implicit private val testingBackend: SttpBackendStub[Future, Nothing, Nothing] = SttpBackendStub.asynchronousFuture[Nothing]
  private val classLoader = getClass.getClassLoader

  it("should authenticate an anonymous user") {

    val anonymousUserRole = "Test"
    val config = ConfigFactory.parseString(
      s"""
        authentication: {
          method: "Dummy"
          anonymousUserRole: "${anonymousUserRole}"
        }
      """.stripMargin)

    val authenticationResources = AuthenticationResources(config, classLoader)
    assert(authenticationResources.isInstanceOf[DummyAuthenticationResources])

    val testRoute =
      authenticationResources.authenticate() { authenticatedUser =>
        get {
          complete(authenticatedUser.roles.mkString)
        }
      }

    Get() ~> testRoute ~> check {
      responseAs[String] shouldEqual s"${anonymousUserRole}"
    }
  }

}
