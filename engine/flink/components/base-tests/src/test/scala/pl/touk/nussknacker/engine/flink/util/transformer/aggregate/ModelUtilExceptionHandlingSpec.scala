package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import cats.data.NonEmptyList
import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.scalatest.funsuite.AnyFunSuite
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, NodeComponentInfo}
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.FlinkBaseUnboundedComponentProvider
import pl.touk.nussknacker.engine.flink.test._
import pl.touk.nussknacker.engine.flink.test.ScalatestMiniClusterJobStatusCheckingOps.miniClusterWithServicesToOps
import pl.touk.nussknacker.engine.flink.util.transformer.FlinkBaseComponentProvider
import pl.touk.nussknacker.engine.flink.util.transformer.join.BranchType
import pl.touk.nussknacker.engine.process.runner.FlinkScenarioUnitTestJob
import pl.touk.nussknacker.engine.spel.SpelExpressionEvaluationException
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData

import java.util.UUID

class ModelUtilExceptionHandlingSpec extends AnyFunSuite with CorrectExceptionHandlingSpec {

  override protected def runScenario(
      env: StreamExecutionEnvironment,
      modelData: ModelData,
      scenario: CanonicalProcess
  ): JobExecutionResult = new FlinkScenarioUnitTestJob(modelData).run(scenario, env)

  private val durationExpression = "T(java.time.Duration).parse('PT1M')"

  test("should handle exceptions in aggregate keys") {
    checkExceptions(FlinkBaseComponentProvider.Components ::: FlinkBaseUnboundedComponentProvider.Components) {
      case (graph, generator) =>
        NonEmptyList.one(
          graph
            .customNode(
              "previousValue",
              "out1",
              "previousValue",
              "groupBy" -> generator.throwFromString().spel,
              "value"   -> generator.throwFromString().spel
            )
            .customNode(
              "aggregate-sliding",
              "out2",
              "aggregate-sliding",
              "groupBy"           -> generator.throwFromString().spel,
              "aggregateBy"       -> generator.throwFromString().spel,
              "aggregator"        -> "#AGG.first".spel,
              "windowLength"      -> durationExpression.spel,
              "emitWhenEventLeft" -> "false".spel
            )
            .customNodeNoOutput(
              "delay",
              "delay",
              "key"   -> generator.throwFromString().spel,
              "delay" -> "T(java.time.Duration).parse('PT0M')".spel,
            )
            .split(
              "branches",
              GraphBuilder
                .customNode(
                  "aggregate-tumbling",
                  "out3",
                  "aggregate-tumbling",
                  "groupBy"      -> generator.throwFromString().spel,
                  "aggregateBy"  -> generator.throwFromString().spel,
                  "aggregator"   -> "#AGG.first".spel,
                  "windowLength" -> durationExpression.spel,
                  "emitWhen" -> "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.TumblingWindowTrigger).OnEvent".spel
                )
                .emptySink("end", "dead-end"),
              GraphBuilder
                .customNode(
                  "aggregate-session",
                  "out3",
                  "aggregate-session",
                  "groupBy"             -> generator.throwFromString().spel,
                  "aggregateBy"         -> generator.throwFromString().spel,
                  "aggregator"          -> "#AGG.first".spel,
                  "sessionTimeout"      -> durationExpression.spel,
                  "endSessionCondition" -> "true".spel,
                  "emitWhen" -> "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.SessionWindowTrigger).OnEvent".spel
                )
                .emptySink("end2", "dead-end"),
              GraphBuilder.branchEnd("union1", "union1"),
              GraphBuilder.branchEnd("union2", "union2"),
            )
        )
    }
  }

  test("should handle exceptions in single side join") {

    val generator = new ExceptionGenerator

    // we do it only once, as test data will be generated for left and right
    val keyParamExpression = generator.throwFromString()

    val scenario = ScenarioBuilder
      .streaming("test")
      .sources(
        GraphBuilder.source("source", "source").branchEnd("left", "join"),
        GraphBuilder.source("source2", "source").branchEnd("right", "join"),
        GraphBuilder
          .join(
            "join",
            "single-side-join",
            Some("out"),
            List(
              (
                "left",
                List(
                  ("key", s"'left' + $keyParamExpression".spel),
                  ("branchType", s"T(${classOf[BranchType].getName}).MAIN".spel)
                )
              ),
              (
                "right",
                List(
                  ("key", s"'right' + $keyParamExpression".spel),
                  ("branchType", s"T(${classOf[BranchType].getName}).JOINED".spel)
                )
              )
            ),
            "aggregator"   -> "#AGG.first".spel,
            "aggregateBy"  -> s"'aggregate' + ${generator.throwFromString()}".spel,
            "windowLength" -> durationExpression.spel
          )
          .emptySink("end4", "dead-end")
      )

    val runId  = UUID.randomUUID().toString
    val config = RecordingExceptionConsumerProvider.configWithProvider(ConfigFactory.empty(), consumerId = runId)
    val sourceComponentDefinition = ComponentDefinition("source", SamplesComponent.create(generator.count))
    val enrichedComponents = sourceComponentDefinition :: FlinkBaseComponentProvider.Components :::
      FlinkBaseUnboundedComponentProvider.Components
    flinkMiniCluster.withDetachedStreamExecutionEnvironment { env =>
      val executionResult = runScenario(env, LocalModelData(config, enrichedComponents), scenario)
      flinkMiniCluster.waitForJobIsFinished(executionResult.getJobID)

      // A bit more complex check, since there are errors from both join sides...
      RecordingExceptionConsumer
        .exceptionsFor(runId)
        .collect { case NuExceptionInfo(Some(NodeComponentInfo("join", _)), e: SpelExpressionEvaluationException, _) =>
          e.expression
        }
        .toSet shouldBe Set(
        "'right' + '' + (1 / #input[0])",
        "'left' + '' + (1 / #input[0])",
        "'aggregate' + '' + (1 / #input[1])"
      )
    }
  }

}
