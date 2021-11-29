package pl.touk.nussknacker.openapi.functional

import com.typesafe.scalalogging.LazyLogging
import org.asynchttpclient.DefaultAsyncHttpClient
import org.scalatest._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.test.EmptyInvocationCollector.Instance
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.util.service.ServiceWithStaticParametersAndReturnType
import pl.touk.nussknacker.openapi.enrichers.{SwaggerEnricherCreator, SwaggerEnrichers}
import pl.touk.nussknacker.openapi.http.backend.FixedAsyncHttpClientBackendProvider
import pl.touk.nussknacker.openapi.parser.SwaggerParser
import pl.touk.nussknacker.openapi.{ApiKeyConfig, OpenAPIServicesConfig}
import pl.touk.nussknacker.test.PatientScalaFutures

import java.net.URL
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source

class OpenAPIServiceSpec extends fixture.FunSuite with BeforeAndAfterAll with Matchers with EitherValues with LazyLogging with PatientScalaFutures {

  implicit val metaData: MetaData = MetaData("testProc", StreamMetaData())
  implicit val contextId: ContextId = ContextId("testContextId")

  type FixtureParam = ServiceWithStaticParametersAndReturnType

  def withFixture(test: OneArgTest): Outcome = {
    val definition = Source.fromInputStream(getClass.getClassLoader.getResourceAsStream("customer-swagger.json")).mkString


    val client = new DefaultAsyncHttpClient()
    try {
      StubService.withCustomerService { port =>
        val securities = Map("apikey" -> ApiKeyConfig("TODO"))
        val config = OpenAPIServicesConfig(securities = Some(securities),
          rootUrl = Some(new URL(s"http://localhost:$port")))
        val services = SwaggerParser.parse(definition, config)

        val enricher = new SwaggerEnrichers(Some(new URL(s"http://localhost:$port")), new SwaggerEnricherCreator(new FixedAsyncHttpClientBackendProvider(client)))
          .enrichers(services, Nil, Map.empty).head.service.asInstanceOf[ServiceWithStaticParametersAndReturnType]
        enricher.open(LiteEngineRuntimeContextPreparer.noOp
          .prepare(JobData(metaData, ProcessVersion.empty, DeploymentData.empty)))

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
