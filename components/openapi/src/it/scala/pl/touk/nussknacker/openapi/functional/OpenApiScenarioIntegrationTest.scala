package pl.touk.nussknacker.openapi.functional

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.LazyLogging
import org.scalatest._
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.modelconfig.DefaultModelConfigLoader
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.helpers.{BaseSampleConfigCreator, ProcessTestHelpers}
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.MockService
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.VeryPatientScalaFutures

class OpenApiScenarioIntegrationTest extends fixture.FunSuite with BeforeAndAfterAll with Matchers with ProcessTestHelpers with LazyLogging with VeryPatientScalaFutures {

  import org.apache.flink.streaming.api.scala._
  import spel.Implicits._

  type FixtureParam = Int

  def withFixture(test: OneArgTest): Outcome = StubService.withCustomerService { port =>
    withFixture(test.toNoArgTest(port))
  }

  test("should run scenario with open api enricher") { port =>
    val finalConfig = ConfigFactory.load()
      .withValue("components.openAPI.url", fromAnyRef(s"http://localhost:$port/swagger"))
      .withValue("components.openAPI.rootUrl", fromAnyRef(s"http://localhost:$port/customers"))
    val resolvedConfig = new DefaultModelConfigLoader().resolveInputConfigDuringExecution(finalConfig, getClass.getClassLoader).config

    val scenario =
      ScenarioBuilder
        .streaming("openapi-test")
        .parallelism(1)
        .source("start", "source")
        .enricher("customer", "customer", "getCustomer", ("customer_id", "#input"))
        .processorEnd("end", "mockService", "all" -> "#customer")

    val data = List("10")
    val creator = new BaseSampleConfigCreator(data)
    processInvoker.invoke(scenario, creator, resolvedConfig, actionToInvokeWithJobRunning = {
      eventually {
        MockService.data shouldBe List(TypedMap(Map("name" -> "Robert Wright", "id" -> 10L, "category" -> "GOLD")))
      }
    })
  }
}
