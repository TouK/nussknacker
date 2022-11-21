package pl.touk.nussknacker.openapi.functional

import cats.data.Validated.Valid
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{Assertion, BeforeAndAfterAll}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.EmptyInvocationCollector.Instance
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.util.runtimecontext.TestEngineRuntimeContext
import pl.touk.nussknacker.engine.util.service.EagerServiceWithStaticParametersAndReturnType
import pl.touk.nussknacker.openapi.enrichers.{SwaggerEnricherCreator, SwaggerEnrichers}
import pl.touk.nussknacker.openapi.parser.SwaggerParser
import pl.touk.nussknacker.openapi.{ApiKeyConfig, OpenAPIServicesConfig, ServiceName}
import pl.touk.nussknacker.test.PatientScalaFutures
import sttp.client.testing.SttpBackendStub
import sttp.client.{Request, Response}
import sttp.model.{HeaderNames, StatusCode}

import java.net.URL
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

class SecurityTest extends AnyFunSuite with BeforeAndAfterAll with Matchers with LazyLogging with PatientScalaFutures {

  implicit val componentUseCase: ComponentUseCase = ComponentUseCase.EngineRuntime
  implicit val metaData: MetaData = MetaData("testProc", StreamMetaData())
  implicit val contextId: ContextId = ContextId("testContextId")
  private val context = TestEngineRuntimeContext(JobData(metaData, ProcessVersion.empty))

  case class Config(path: String,
                    securityName: String,
                    serviceName: String,
                    key: String,
                    assertion: Request[_, _] => Assertion)

  private val configs = List[Config](
    Config("headerPath", "headerConfig", "header", "h1", _.headers.find(_.name == "keyHeader").map(_.value) shouldBe Some("h1")),
    Config("queryPath", "queryConfig", "query", "q1", _.uri.params.get("keyParam") shouldBe Some("q1")),
    Config("cookiePath", "cookieConfig", "cookie", "c1", _.headers.find(_.name == HeaderNames.Cookie).map(_.value) shouldBe Some("keyCookie=c1")),
  )

  test("service returns customers") {
    val backend = SttpBackendStub.asynchronousFuture.whenRequestMatches { request =>
      val pathMatches = configs.find(_.path == request.uri.path.head)
      pathMatches.foreach(_.assertion(request))
      pathMatches.isDefined
    }.thenRespond(Response("{}", StatusCode.Ok))

    val withCorrectConfig =
      enrichersForSecurityConfig(backend, configs.map(c => c.securityName -> ApiKeyConfig(c.key)).toMap)
    configs.foreach { config =>
      withClue(config.serviceName) {
        withCorrectConfig(ServiceName(config.serviceName)).invoke(Map()).futureValue shouldBe TypedMap(Map.empty)
      }
    }

    val withBadConfig = enrichersForSecurityConfig(backend, configs.map(c => c.securityName -> ApiKeyConfig("bla")).toMap)
    configs.foreach { config =>
      withClue(config.serviceName) {
        intercept[Exception] {
          withBadConfig(ServiceName(config.serviceName)).invoke(Map()).futureValue
        }
      }
    }


  }

  private def enrichersForSecurityConfig(backend: SttpBackendStub[Future, Nothing, Nothing], securities: Map[String, ApiKeyConfig]) = {
    val definition = Source.fromInputStream(getClass.getClassLoader.getResourceAsStream("service-security.yml")).mkString
    val config = OpenAPIServicesConfig(security = Some(securities))
    val services = SwaggerParser.parse(definition, config).collect {
      case Valid(service) => service
    }

    val enrichers = new SwaggerEnrichers(new URL("http://foo"), None,
      new SwaggerEnricherCreator((_: ExecutionContext) => backend))
      .enrichers(services, Nil, Map.empty).map(ed => ed.name -> ed.service.asInstanceOf[EagerServiceWithStaticParametersAndReturnType]).toMap
    enrichers.foreach(_._2.open(context))
    enrichers
  }
}
