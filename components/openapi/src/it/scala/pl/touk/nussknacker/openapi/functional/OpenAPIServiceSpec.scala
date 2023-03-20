package pl.touk.nussknacker.openapi.functional

import cats.data.Validated.Valid
import com.typesafe.scalalogging.LazyLogging
import org.asynchttpclient.DefaultAsyncHttpClient
import org.scalatest.{BeforeAndAfterAll, Outcome}
import org.scalatest.funsuite.FixtureAnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.EmptyInvocationCollector.Instance
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.util.ResourceLoader
import pl.touk.nussknacker.engine.util.runtimecontext.TestEngineRuntimeContext
import pl.touk.nussknacker.engine.util.service.EagerServiceWithStaticParametersAndReturnType
import pl.touk.nussknacker.openapi.enrichers.{SwaggerEnricherCreator, SwaggerEnrichers}
import pl.touk.nussknacker.openapi.http.backend.FixedAsyncHttpClientBackendProvider
import pl.touk.nussknacker.openapi.parser.SwaggerParser
import pl.touk.nussknacker.openapi.{ApiKeyConfig, OpenAPIServicesConfig}
import pl.touk.nussknacker.test.PatientScalaFutures

import java.net.URL
import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext.Implicits.global

class OpenAPIServiceSpec extends FixtureAnyFunSuite with BeforeAndAfterAll with Matchers with LazyLogging with PatientScalaFutures {

  implicit val componentUseCase: ComponentUseCase = ComponentUseCase.EngineRuntime
  implicit val metaData: MetaData = MetaData("testProc", StreamMetaData())
  implicit val contextId: ContextId = ContextId("testContextId")

  type FixtureParam = EagerServiceWithStaticParametersAndReturnType

  def withFixture(test: OneArgTest): Outcome = {
    val definition = ResourceLoader.load("/customer-swagger.json")


    val client = new DefaultAsyncHttpClient()
    try {
      new StubService().withCustomerService { port =>
        val securities = Map("apikey" -> ApiKeyConfig("TODO"))
        val config = OpenAPIServicesConfig(new URL("http://foo"), security = Some(securities),
          rootUrl = Some(new URL(s"http://localhost:$port")))
        val services = SwaggerParser.parse(definition, config).collect {
          case Valid(service) => service
        }

        val enricher = SwaggerEnrichers.prepare(config, services,
          new SwaggerEnricherCreator(new FixedAsyncHttpClientBackendProvider(client))).head.service.asInstanceOf[EagerServiceWithStaticParametersAndReturnType]
        enricher.open(TestEngineRuntimeContext(JobData(metaData, ProcessVersion.empty)))

        withFixture(test.toNoArgTest(enricher))
      }
    } finally {
      client.close()
    }
  }

  test("service returns customers") { service =>

    val valueWithChosenFields = service.invoke(Map("customer_id" -> "10")).futureValue.asInstanceOf[TypedMap].asScala
    valueWithChosenFields shouldEqual Map("name" -> "Robert Wright", "id" -> 10, "category" -> "GOLD")
  }

}
