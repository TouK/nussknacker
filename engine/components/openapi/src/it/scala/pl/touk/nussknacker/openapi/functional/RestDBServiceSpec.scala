package pl.touk.nussknacker.openapi.functional

import com.typesafe.scalalogging.LazyLogging
import org.scalatest._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.definition.ServiceWithExplicitMethod
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.test.EmptyInvocationCollector.Instance
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.standalone.api.{StandaloneContextLifecycle, StandaloneContextPreparer}
import pl.touk.nussknacker.engine.standalone.metrics.NoOpMetricsProvider
import pl.touk.nussknacker.engine.standalone.utils.service.TimeMeasuringService
import pl.touk.nussknacker.openapi
import pl.touk.nussknacker.openapi.ApiKeyConfig
import pl.touk.nussknacker.openapi.enrichers.{BaseSwaggerEnricher, BaseSwaggerEnricherCreator, SwaggerEnrichers}
import pl.touk.nussknacker.openapi.parser.SwaggerParser
import pl.touk.nussknacker.test.PatientScalaFutures
import sttp.client.SttpBackend
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import java.net.URL
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

class RestDBServiceSpec extends fixture.FunSuite with BeforeAndAfterAll with Matchers with EitherValues with LazyLogging with PatientScalaFutures {

  implicit val metaData: MetaData = MetaData("testProc", StreamMetaData())
  implicit val contextId: ContextId = ContextId("testContextId")

  type FixtureParam = ServiceWithExplicitMethod

  def withFixture(test: OneArgTest): Outcome = {
    val definition = Source.fromInputStream(getClass.getClassLoader.getResourceAsStream("customer-swagger.json")).mkString
    val securities = Map("apikey" -> ApiKeyConfig("TODO"))
    val services = SwaggerParser.parse(definition, securities)

    val backend = AsyncHttpClientFutureBackend()
    try {
      StubService.withCustomerService { port =>
        val enricher = new SwaggerEnrichers(Some(new URL(s"http://localhost:$port")), new SimpleEnricherCreator(backend))
          .enrichers(services, Nil, Map.empty).head.service.asInstanceOf[ServiceWithExplicitMethod with StandaloneContextLifecycle]
        enricher.open(JobData(metaData, ProcessVersion.empty, DeploymentData.empty), new StandaloneContextPreparer(NoOpMetricsProvider).prepare("1"))

        withFixture(test.toNoArgTest(enricher))
      }
    } finally {
      backend.close()
    }
  }

  test("companies") { service =>

    val valueWithChosenFields = service.invokeService(List("10")).futureValue.asInstanceOf[TypedMap].asScala
    valueWithChosenFields shouldEqual Map("name" -> "Robert Wright", "id" -> 10, "category" -> "GOLD")
  }

  class SimpleEnricherCreator(backend: SttpBackend[Future, Nothing, Nothing]) extends BaseSwaggerEnricherCreator {
    override def create(rootUrl: Option[URL], swaggerService: openapi.SwaggerService, fixedParams: Map[String, () => AnyRef]): BaseSwaggerEnricher
    = new BaseSwaggerEnricher(rootUrl, swaggerService, fixedParams) with TimeMeasuringService {
      override implicit protected def httpBackendForEc(implicit ec: ExecutionContext): SttpBackend[Future, Nothing, Nothing] = backend
    }
  }


}
