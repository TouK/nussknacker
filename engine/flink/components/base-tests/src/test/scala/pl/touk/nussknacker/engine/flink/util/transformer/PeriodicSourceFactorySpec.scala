package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.ConfigFactory
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.FlinkBaseUnboundedComponentProvider
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.process.helpers.ConfigCreatorWithCollectingListener
import pl.touk.nussknacker.engine.process.runner.UnitTestsFlinkRunner
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.ResultsCollectingListenerHolder
import pl.touk.nussknacker.test.PatientScalaFutures

class PeriodicSourceFactorySpec extends AnyFunSuite with FlinkSpec with PatientScalaFutures with Matchers with Inside {

  test("should produce results for each element in list") {
    val sinkId = "sinkId"
    val input  = "some value"

    val collectingListener = ResultsCollectingListenerHolder.registerRun
    val model = LocalModelData(
      ConfigFactory.empty(),
      FlinkBaseComponentProvider.Components ::: FlinkBaseUnboundedComponentProvider.Components,
      configCreator = new ConfigCreatorWithCollectingListener(collectingListener),
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
    UnitTestsFlinkRunner.registerInEnvironmentWithModel(stoppableEnv, model)(scenario)

    val id = stoppableEnv.executeAndWaitForStart(scenario.name.value)
    try {
      eventually {
        val results = collectingListener.results.nodeResults.get(sinkId)
        results.flatMap(_.headOption).flatMap(_.get[String]("input")) shouldBe Some(input)
      }
    } finally {
      stoppableEnv.cancel(id.getJobID)
    }

  }

}
