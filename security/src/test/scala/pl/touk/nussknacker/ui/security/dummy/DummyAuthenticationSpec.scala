package pl.touk.nussknacker.ui.security.dummy

import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.ui.security.api.{AuthenticationProvider, AuthenticationResources}
import sttp.client3.{Identity, SttpBackend}
import sttp.client3.testing.SttpBackendStub

import scala.concurrent.Future

class DummyAuthenticationSpec extends AnyFunSpec with Matchers with ScalatestRouteTest with Directives {

  implicit private val testingBackend: SttpBackendStub[Future, Any] = SttpBackendStub.asynchronousFuture
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
