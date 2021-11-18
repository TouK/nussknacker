package pl.touk.nussknacker.openapi.functional

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.LazyLogging
import org.scalatest._
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.modelconfig.DefaultModelConfigLoader
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.helpers.BaseSampleConfigCreator
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.MockService
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.VeryPatientScalaFutures

class SampleProcessWithRestDBServiceSpec extends fixture.FunSuite with BeforeAndAfterAll with Matchers with FlinkSpec with EitherValues with LazyLogging with VeryPatientScalaFutures {

  import org.apache.flink.streaming.api.scala._
  import spel.Implicits._

  type FixtureParam = Int

  def withFixture(test: OneArgTest): Outcome = StubService.withCustomerService { port =>
    withFixture(test.toNoArgTest(port))
  }

  test("should enrich scenario with data") { port =>

    val scenario =
      EspProcessBuilder
        .id("opeanapi-test")
        .parallelism(1)
        .source("start", "source")
        .enricher("customer", "customer", "getCustomer", ("customer_id", "#input"))
        .processorEnd("end", "mockService", "all" -> "#customer")

    val data = List("10")
    run(scenario, data, port)
    MockService.data shouldBe List(TypedMap(Map("name" -> "Robert Wright", "id" -> 10L, "category" -> "GOLD")))
  }

  def run(process: EspProcess, data: List[String], port: Int): Unit = {
    val env = flinkMiniCluster.createExecutionEnvironment()
    val finalConfig = ConfigFactory.load()
      .withValue("components.openAPI.url", fromAnyRef(s"http://localhost:$port/swagger"))
      .withValue("components.openAPI.rootUrl", fromAnyRef(s"http://localhost:$port/customers"))
    val resolvedConfig =  new DefaultModelConfigLoader().resolveInputConfigDuringExecution(finalConfig, getClass.getClassLoader).config
    val modelData = LocalModelData(resolvedConfig, new BaseSampleConfigCreator(data))
    val registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), ExecutionConfigPreparer.unOptimizedChain(modelData))
    registrar.register(new StreamExecutionEnvironment(env), process, ProcessVersion.empty, DeploymentData.empty)
    env.executeAndWaitForFinished(process.id)()
  }

}
