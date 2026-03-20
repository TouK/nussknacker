package pl.touk.nussknacker.openapi.functional

import cats.data.Validated.Valid
import com.typesafe.scalalogging.LazyLogging
import org.asynchttpclient.DefaultAsyncHttpClient
import org.scalatest.{BeforeAndAfterAll, Outcome}
import org.scalatest.funsuite.FixtureAnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ComponentUseContext
import pl.touk.nussknacker.engine.api.test.EmptyInvocationCollector.Instance
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.util.ResourceLoader
import pl.touk.nussknacker.engine.util.runtimecontext.TestEngineRuntimeContext
import pl.touk.nussknacker.engine.util.service.AsyncExecutionTimeMeasurement
import pl.touk.nussknacker.http.backend.FixedAsyncHttpClientBackendProvider
import pl.touk.nussknacker.openapi.{ApiKeySecret, OpenAPIServicesConfig, SecuritySchemeName}
import pl.touk.nussknacker.openapi.enrichers.OpenAPIEnricher
import pl.touk.nussknacker.openapi.parser.SwaggerParser
import pl.touk.nussknacker.test.PatientScalaFutures

import java.net.URL
import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters._

class OpenAPIServiceSpec
    extends FixtureAnyFunSuite
    with BeforeAndAfterAll
    with Matchers
    with LazyLogging
    with PatientScalaFutures {

  implicit val componentUseContext: ComponentUseContext = ComponentUseContext.LiveRuntime(None)
  implicit val metaData: MetaData                       = MetaData("testProc", StreamMetaData())
  implicit val context: Context                         = Context.dummy
  val jobData = JobData(metaData, ProcessVersion.empty.copy(processName = metaData.name))

  type FixtureParam = ServiceInvoker

  def withFixture(test: OneArgTest): Outcome = {
    val definition = ResourceLoader.load("/customer-swagger.json")

    val clientProvider = new FixedAsyncHttpClientBackendProvider(new DefaultAsyncHttpClient())
    try {
      new StubService().withCustomerService { port =>
        val secretBySchemeName = Map(SecuritySchemeName("apikey") -> ApiKeySecret("TODO"))
        val config = OpenAPIServicesConfig(
          new URL("http://foo"),
          security = secretBySchemeName,
          rootUrl = Some(new URL(s"http://localhost:$port"))
        )
        val services = SwaggerParser.parse(definition, config).collect { case Valid(service) =>
          service
        }

        val enricher = OpenAPIEnricher(
          service = services.head,
          config = config,
          clientProvider = clientProvider,
          params = Params.fromRawValuesMap(Map(ParameterName("customer_id") -> "10")),
          getTimeMeasurement =
            () => new AsyncExecutionTimeMeasurement(TestEngineRuntimeContext(jobData), "openAPI", Map.empty)
        )

        withFixture(test.toNoArgTest(enricher))
      }
    } finally {
      clientProvider.close()
    }
  }

  test("service returns customers") { service =>
    val valueWithChosenFields =
      service
        .invoke(context)
        .futureValue
        .asInstanceOf[TypedMap]
        .asScala
    valueWithChosenFields shouldEqual Map("name" -> "Robert Wright", "id" -> 10, "category" -> "GOLD")
  }

}
