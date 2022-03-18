package pl.touk.nussknacker.openapi.functional

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.IOUtils
import org.scalatest._
import pl.touk.nussknacker.engine.api.process.WithCategories
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.{CirceUtil, ProcessVersion, Service}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.modelconfig.DefaultModelConfigLoader
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.{FlinkProcessCompiler, MockFlinkProcessCompiler}
import pl.touk.nussknacker.engine.process.helpers.BaseSampleConfigCreator
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.MockService
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig
import pl.touk.nussknacker.engine.util.config.CustomFicusInstances.{toFicusConfig, urlValueReader}
import pl.touk.nussknacker.openapi.OpenAPIsConfig.openAPIServicesConfigVR
import pl.touk.nussknacker.openapi.{OpenAPIServicesConfig, SwaggerService}
import pl.touk.nussknacker.openapi.enrichers.SwaggerEnricher
import pl.touk.nussknacker.openapi.http.backend.HttpBackendProvider
import pl.touk.nussknacker.openapi.parser.SwaggerParser
import pl.touk.nussknacker.test.VeryPatientScalaFutures
import sttp.client.Response
import sttp.client.testing.SttpBackendStub

import java.net.URL
import java.nio.charset.StandardCharsets
import scala.concurrent.{ExecutionContext, Future}

class SampleProcessWithRestDBServiceSpec extends fixture.FunSuite with BeforeAndAfterAll with Matchers with FlinkSpec with LazyLogging with VeryPatientScalaFutures {

  import org.apache.flink.streaming.api.scala._
  import spel.Implicits._

  type FixtureParam = Int

  def rootUrl(port: Int): String = s"http://localhost:$port/customers"

  def withFixture(test: OneArgTest): Outcome = StubService.withCustomerService { port =>
    withFixture(test.toNoArgTest(port))
  }

  val stubbedBackend: SttpBackendStub[Future, Nothing, Nothing] = SttpBackendStub.asynchronousFuture[Nothing]
  val stubbedBackedProvider: HttpBackendProvider = (_: ExecutionContext) => stubbedBackend


  test("should enrich scenario with data") { port =>
    val finalConfig = ConfigFactory.load()
      .withValue("components.openAPI.url", fromAnyRef(s"http://localhost:$port/swagger"))
      .withValue("components.openAPI.rootUrl", fromAnyRef(rootUrl(port)))
    val openAPIsConfig = config.rootAs[OpenAPIServicesConfig]
    val definition = IOUtils.toString(finalConfig.as[URL]("components.openAPI.url"), StandardCharsets.UTF_8)
    val services = SwaggerParser.parse(definition, openAPIsConfig)

    val stubbedGetCustomerOpenApiService: SwaggerEnricher = new SwaggerEnricher(Some(new URL(rootUrl(port))), services.head, Map.empty, stubbedBackedProvider)

    //when
    val scenario =
      ScenarioBuilder
        .streaming("opeanapi-test")
        .parallelism(1)
        .source("start", "source")
        .enricher("customer", "customer", "getCustomer", ("customer_id", "#input"))
        .processorEnd("end", "mockService", "all" -> "#customer")

    run(scenario, List("10"), port, stubbedGetCustomerOpenApiService)
    //then

    MockService.data shouldBe List(TypedMap(Map("name" -> "Robert Wright", "id" -> 10L, "category" -> "GOLD")))
  }

  def run(process: EspProcess, data: List[String], port: Int, stubbedGetCustomerOpenApiService: SwaggerEnricher): Unit = {
    val env = flinkMiniCluster.createExecutionEnvironment()
    val finalConfig = ConfigFactory.load()
      .withValue("components.openAPI.url", fromAnyRef(s"http://localhost:$port/swagger"))
      .withValue("components.openAPI.rootUrl", fromAnyRef(s"http://localhost:$port/customers"))
    val resolvedConfig =  new DefaultModelConfigLoader().resolveInputConfigDuringExecution(finalConfig, getClass.getClassLoader).config
    val modelData = LocalModelData(resolvedConfig, new BaseSampleConfigCreator(data))
    val services: Map[String, WithCategories[Service]] = Map(
      "asdf" -> WithCategories(stubbedGetCustomerOpenApiService)
    )
    val registrar = FlinkProcessRegistrar(new MockFlinkProcessCompiler(services, process, modelData), ExecutionConfigPreparer.unOptimizedChain(modelData))
    registrar.register(new StreamExecutionEnvironment(env), process, ProcessVersion.empty, DeploymentData.empty)
    env.executeAndWaitForFinished(process.id)()
  }

}
