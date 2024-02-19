package pl.touk.nussknacker.openapi

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Assertion, BeforeAndAfterAll}
import pl.touk.nussknacker.engine.api.{ContextId, Params}
import pl.touk.nussknacker.engine.api.test.EmptyInvocationCollector.Instance
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.test.PatientScalaFutures
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{Request, Response}
import sttp.model.{HeaderNames, StatusCode}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SecurityTest
    extends AnyFunSuite
    with BeforeAndAfterAll
    with Matchers
    with LazyLogging
    with PatientScalaFutures
    with BaseOpenAPITest {

  sealed case class Config(
      path: String,
      securityName: String,
      serviceName: String,
      key: String,
      assertion: Request[_, _] => Assertion
  )

  private val configs = List[Config](
    Config(
      "headerPath",
      "headerConfig",
      "header",
      "h1",
      _.headers.find(_.name == "keyHeader").map(_.value) shouldBe Some("h1")
    ),
    Config("queryPath", "queryConfig", "query", "q1", _.uri.params.get("keyParam") shouldBe Some("q1")),
    Config(
      "cookiePath",
      "cookieConfig",
      "cookie",
      "c1",
      _.headers.find(_.name == HeaderNames.Cookie).map(_.value) shouldBe Some("keyCookie=c1")
    ),
  )

  test("service returns customers") {
    val backend = SttpBackendStub.asynchronousFuture
      .whenRequestMatches { request =>
        val pathMatches = configs.find(_.path == request.uri.path.head)
        pathMatches.foreach(_.assertion(request))
        pathMatches.isDefined
      }
      .thenRespond(Response("{}", StatusCode.Ok))

    val withCorrectConfig =
      enrichersForSecurityConfig(backend, configs.map(c => c.securityName -> ApiKeyConfig(c.key)).toMap)
    configs.foreach { config =>
      withClue(config.serviceName) {
        implicit val contextId: ContextId = ContextId("1")
        withCorrectConfig(ServiceName(config.serviceName))
          .invoke(Params.empty)
          .futureValue shouldBe TypedMap(Map.empty)
      }
    }

    val withBadConfig =
      enrichersForSecurityConfig(backend, configs.map(c => c.securityName -> ApiKeyConfig("bla")).toMap)
    configs.foreach { config =>
      withClue(config.serviceName) {
        intercept[Exception] {
          implicit val contextId: ContextId = ContextId("1")
          withBadConfig(ServiceName(config.serviceName)).invoke(Params.empty).futureValue
        }
      }
    }
  }

  private def enrichersForSecurityConfig(
      backend: SttpBackendStub[Future, Any],
      securities: Map[String, ApiKeyConfig]
  ) = {
    parseToEnrichers("service-security.yml", backend, baseConfig.copy(security = Some(securities)))
  }

}
