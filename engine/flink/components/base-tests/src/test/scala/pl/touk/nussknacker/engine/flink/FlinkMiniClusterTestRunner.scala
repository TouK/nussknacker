package pl.touk.nussknacker.engine.flink

import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.api.{ContextId, NodeId}
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.SourceFactory
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.WatermarkStrategyUtils
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.test.ScalatestMiniClusterJobStatusCheckingOps.miniClusterWithServicesToOps
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.flink.util.transformer.FlinkBaseComponentProvider
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateWindowsConfig
import pl.touk.nussknacker.engine.process.helpers.ConfigCreatorWithCollectingListener
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.LogService
import pl.touk.nussknacker.engine.process.runner.FlinkScenarioUnitTestJob
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, ResultsCollectingListenerHolder}
import pl.touk.nussknacker.engine.testmode.TestProcess.{NodeTransition, TestResults}
import pl.touk.nussknacker.engine.util.config.DocsConfig
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper

import java.time.{Duration, Instant}
import scala.jdk.CollectionConverters._
import scala.util.Try

trait FlinkMiniClusterTestRunner { _: FlinkSpec =>

  protected def sourcesWithMockedData: Map[String, List[Int]]

  protected def withCollectingTestResults(
      canonicalProcess: CanonicalProcess,
      assertions: TestResults[Any] => Unit,
      allowEndingScenarioWithoutSink: Boolean = false,
  ): Unit = {
    ResultsCollectingListenerHolder.withListener { collectingListener =>
      val model = modelData(collectingListener, AggregateWindowsConfig.Default, allowEndingScenarioWithoutSink)
      flinkMiniCluster.withDetachedStreamExecutionEnvironment { env =>
        val executionResult = new FlinkScenarioUnitTestJob(model).run(canonicalProcess, env)
        flinkMiniCluster.waitForJobIsFinished(executionResult.getJobID)
        assertions(collectingListener.results)
      }
    }
  }

  private def modelData(
      collectingListener: => ResultsCollectingListener[Any],
      aggregateWindowsConfig: AggregateWindowsConfig,
      allowEndingScenarioWithoutSink: Boolean,
  ): LocalModelData = {
    def sourceComponent(data: List[Int]) = {
      val watermarkStrategy = WatermarkStrategyUtils.afterEachEvent[Int](
        (_: Int, _: Long) => Instant.now.toEpochMilli,
        Duration.ofHours(1)
      )
      SourceFactory.noParamUnboundedStreamFactory[Int](
        CollectionSource(data, Some(watermarkStrategy), Typed.typedClass[Int])
      )
    }
    val config =
      if (allowEndingScenarioWithoutSink) {
        ConfigFactory.parseString("""allowEndingScenarioWithoutSink: true""")
      } else {
        ConfigFactory.empty()
      }
    LocalModelData(
      config,
      List(
        sourcesWithMockedData.toList.map { case (name, data) =>
          ComponentDefinition(name, sourceComponent(data))
        },
        FlinkBaseUnboundedComponentProvider.create(
          DocsConfig.Default,
          aggregateWindowsConfig
        ),
        FlinkBaseComponentProvider.Components,
        List(
          ComponentDefinition("loggerService", LogService)
        ),
      ).flatten,
      configCreator = new ConfigCreatorWithCollectingListener(collectingListener),
    )
  }

  protected def transitionVariables(
      testResults: TestResults[Any],
      fromNodeId: String,
      toNodeId: Option[String]
  ): Set[Map[String, Any]] =
    transitionVariablesByContextId(testResults, fromNodeId, toNodeId).values.toSet

  protected def transitionVariablesByContextId(
      testResults: TestResults[Any],
      fromNodeId: String,
      toNodeId: Option[String]
  ): Map[ContextId, Map[String, Any]] = {
    testResults
      .nodeTransitionResults(NodeTransition(NodeId(fromNodeId), toNodeId.map(NodeId(_))))
      .map { result =>
        (result.id, result.variables.map { case (key, value) => (key, scalaMap(value)) })
      }
      .toMap
  }

  private def scalaMap(value: Any): Any = {
    value match {
      case hashMap: java.util.HashMap[_, _] => hashMap.asScala.toMap
      case other                            => other
    }
  }

  protected def assertNumberOfSamplesThatFinishedInNode(testResults: TestResults[Any], sinkId: String, expected: Int) =
    testResults.nodeTransitionResults.get(NodeTransition(NodeId(sinkId), None)).map(_.length) shouldBe Some(expected)

  protected def catchExceptionMessage(f: => Any): String = Try(f).failed.get.getMessage

}
