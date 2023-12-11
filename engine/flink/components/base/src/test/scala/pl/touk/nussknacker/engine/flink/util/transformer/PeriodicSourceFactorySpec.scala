package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.ConfigFactory
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.helpers.ConfigCreatorWithListener
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.ResultsCollectingListenerHolder
import pl.touk.nussknacker.test.PatientScalaFutures

class PeriodicSourceFactorySpec extends AnyFunSuite with FlinkSpec with PatientScalaFutures with Matchers with Inside {

  test("should produce results for each element in list") {
    val sinkId = "sinkId"
    val input  = "some value"

    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)
    val model = LocalModelData(
      ConfigFactory.empty(),
      FlinkBaseComponentProvider.Components,
      configCreator = new ConfigCreatorWithListener(collectingListener),
    )
    val scenario = ScenarioBuilder
      .streaming("test")
      .source(
        "periodic",
        "periodic",
        "period" -> "T(java.time.Duration).ofSeconds(1)",
        "count"  -> "1",
        "value"  -> s"'$input'"
      )
      .emptySink(sinkId, "dead-end")

    val stoppableEnv = flinkMiniCluster.createExecutionEnvironment()
    val registrar =
      FlinkProcessRegistrar(new FlinkProcessCompiler(model), ExecutionConfigPreparer.unOptimizedChain(model))
    registrar.register(stoppableEnv, scenario, ProcessVersion.empty, DeploymentData.empty)

    val id = stoppableEnv.executeAndWaitForStart(scenario.id)
    try {
      eventually {
        val results = collectingListener.results[Any].nodeResults.get(sinkId)
        results.flatMap(_.headOption).flatMap(_.variableTyped[String]("input")) shouldBe Some(input)
      }
    } finally {
      stoppableEnv.cancel(id.getJobID)
    }

  }

}
