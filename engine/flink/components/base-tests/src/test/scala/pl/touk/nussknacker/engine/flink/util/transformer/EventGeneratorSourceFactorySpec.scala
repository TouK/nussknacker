package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.ConfigFactory
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.FlinkBaseUnboundedComponentProvider
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.test.ScalatestMiniClusterJobStatusCheckingOps.miniClusterWithServicesToOps
import pl.touk.nussknacker.engine.process.helpers.ConfigCreatorWithCollectingListener
import pl.touk.nussknacker.engine.process.runner.FlinkScenarioUnitTestJob
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.ResultsCollectingListenerHolder
import pl.touk.nussknacker.test.PatientScalaFutures

class EventGeneratorSourceFactorySpec
    extends AnyFunSuite
    with FlinkSpec
    with PatientScalaFutures
    with Matchers
    with Inside {

  test("should produce results for each element in list") {
    val sinkId = "sinkId"
    val input  = "some value"

    ResultsCollectingListenerHolder.withListener { collectingListener =>
      val model = LocalModelData(
        ConfigFactory.empty(),
        FlinkBaseComponentProvider.Components ::: FlinkBaseUnboundedComponentProvider.Components,
        configCreator = new ConfigCreatorWithCollectingListener(collectingListener),
      )
      val scenario = ScenarioBuilder
        .streaming("test")
        .source(
          "event-generator",
          "event-generator",
          "schedule" -> "T(java.time.Duration).ofSeconds(1)".spel,
          "count"    -> "1".spel,
          "value"    -> s"'$input'".spel
        )
        .emptySink(sinkId, "dead-end")

      flinkMiniCluster.withDetachedStreamExecutionEnvironment { env =>
        val executionResult = new FlinkScenarioUnitTestJob(model).run(scenario, env)
        flinkMiniCluster.withRunningJob(executionResult.getJobID) {
          eventually {
            val results = collectingListener.results.nodeResults.get(sinkId)
            results.flatMap(_.headOption).flatMap(_.variableTyped("input")) shouldBe Some(input)
          }
        }
      }
    }

  }

  test("should produce n individually evaluated results for n count") {
    val sinkId = "sinkId"

    ResultsCollectingListenerHolder.withListener { collectingListener =>
      val model = LocalModelData(
        ConfigFactory.empty(),
        FlinkBaseComponentProvider.Components ::: FlinkBaseUnboundedComponentProvider.Components,
        configCreator = new ConfigCreatorWithCollectingListener(collectingListener),
      )
      val scenario = ScenarioBuilder
        .streaming("test")
        .source(
          "event-generator",
          "event-generator",
          "schedule" -> "T(java.time.Duration).ofSeconds(1)".spel,
          "count"    -> "2".spel,
          "value"    -> s"T(java.util.UUID).randomUUID".spel
        )
        .emptySink(sinkId, "dead-end")

      flinkMiniCluster.withDetachedStreamExecutionEnvironment { env =>
        val executionResult = new FlinkScenarioUnitTestJob(model).run(scenario, env)

        flinkMiniCluster.withRunningJob(executionResult.getJobID) {
          eventually {
            val results        = collectingListener.results.nodeResults.get(sinkId)
            val emittedResults = results.toList.flatten.flatMap(_.variableTyped("input"))
            emittedResults.size should be > 1
            emittedResults.distinct.size shouldBe emittedResults.size
          }
        }
      }
    }
  }

}
