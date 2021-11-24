package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import com.typesafe.config.ConfigFactory
import org.scalatest.FunSuite
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.CustomStreamTransformer
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process.{ExpressionConfig, ProcessObjectDependencies, WithCategories}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.flink.test._
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.sampleTransformers.{SessionWindowAggregateTransformer, SlidingAggregateTransformerV2, TumblingAggregateTransformer}
import pl.touk.nussknacker.engine.flink.util.transformer.join.{BranchType, SingleSideJoinTransformer}
import pl.touk.nussknacker.engine.flink.util.transformer.{DelayTransformer, PreviousValueTransformer}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.runner.TestFlinkRunner
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.spel.SpelExpressionEvaluationException
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

import java.util.UUID

class ModelUtilExceptionHandlingSpec extends FunSuite with CorrectExceptionHandlingSpec {

  override protected def registerInEnvironment(env: MiniClusterExecutionEnvironment, modelData: ModelData, scenario: EspProcess): Unit
  = TestFlinkRunner.registerInEnvironmentWithModel(env, modelData)(scenario)

  private val durationExpression = "T(java.time.Duration).parse('PT1M')"

  private val configCreator = new EmptyProcessConfigCreator() {
    override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] =
      Map(
        "previousValue" -> WithCategories(PreviousValueTransformer),
        "aggregate-sliding" -> WithCategories(SlidingAggregateTransformerV2),
        "aggregate-tumbling" -> WithCategories(TumblingAggregateTransformer),
        "aggregate-session" -> WithCategories(SessionWindowAggregateTransformer),
        "single-side-join" -> WithCategories(SingleSideJoinTransformer),
        "delay" -> WithCategories(DelayTransformer)
      )

    override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig
    = super.expressionConfig(processObjectDependencies).copy(globalProcessVariables = Map("AGG" -> WithCategories(new AggregateHelper)))

  }

  test("should handle exceptions in aggregate keys") {
    checkExceptions(configCreator) { case (graph, generator) =>
      graph
        .customNode("previousValue", "out1", "previousValue",
          "groupBy" -> generator.throwFromString(),
          "value" -> generator.throwFromString()
        )
        .customNode("aggregate-sliding", "out2", "aggregate-sliding",
          "groupBy" -> generator.throwFromString(),
          "aggregateBy" -> generator.throwFromString(),
          "aggregator" -> "#AGG.first",
          "windowLength" -> durationExpression,
          "emitWhenEventLeft" -> "false"
        )
        .customNodeNoOutput("delay", "delay",
          "key" -> generator.throwFromString(),
          "delay" -> "T(java.time.Duration).parse('PT0M')",
        )
        .split("branches",
          GraphBuilder.customNode("aggregate-tumbling", "out3", "aggregate-tumbling",
            "groupBy" -> generator.throwFromString(),
            "aggregateBy" -> generator.throwFromString(),
            "aggregator" -> "#AGG.first",
            "windowLength" -> durationExpression,
            "emitWhen" -> "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.TumblingWindowTrigger).OnEvent"
          ).emptySink("end", "empty"),
          GraphBuilder.customNode("aggregate-session", "out3", "aggregate-session",
            "groupBy" -> generator.throwFromString(),
            "aggregateBy" -> generator.throwFromString(),
            "aggregator" -> "#AGG.first",
            "sessionTimeout" -> durationExpression,
            "endSessionCondition" -> "true",
            "emitWhen" -> "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.SessionWindowTrigger).OnEvent"
          ).emptySink("end2", "empty"),
          GraphBuilder.branchEnd("union1", "union1"),
          GraphBuilder.branchEnd("union2", "union2"),
        )
    }
  }

  test("should handle exceptions in single side join") {

    val generator = new ExceptionGenerator
    val scenarioBase = EspProcessBuilder.id("test")
      .source("source", "source").branchEnd("left", "join")

    //we do it only once, as test data will be generated for left and right
    val keyParamExpression = generator.throwFromString()

    val scenario = scenarioBase.copy(
      roots = scenarioBase.roots ++ List(
        GraphBuilder.source("source2", "source").branchEnd("right", "join"),
        GraphBuilder.branch("join", "single-side-join", Some("out"),
          List(("left", List(("key", s"'left' + $keyParamExpression"), ("branchType", s"T(${classOf[BranchType].getName}).MAIN"))),
            ("right", List(("key", s"'right' + $keyParamExpression"), ("branchType", s"T(${classOf[BranchType].getName}).JOINED")))),
          "aggregator" -> "#AGG.first",
          "aggregateBy" -> s"'aggregate' + ${generator.throwFromString()}",
          "windowLength" -> durationExpression
        ).emptySink("end4", "empty"),
      )
    )

    val runId = UUID.randomUUID().toString
    val config = RecordingExceptionConsumerProvider.configWithProvider(ConfigFactory.empty(), consumerId = runId)
    val recordingCreator = new RecordingConfigCreator(configCreator, generator.count)
    val env = flinkMiniCluster.createExecutionEnvironment()
    registerInEnvironment(env, LocalModelData(config, recordingCreator), scenario)

    env.executeAndWaitForFinished("test")()

    //A bit more complex check, since there are errors from both join sides...
    RecordingExceptionConsumer.dataFor(runId).collect {
      case NuExceptionInfo(Some("join"), e: SpelExpressionEvaluationException, _) => e.expression
    }.toSet shouldBe Set("'right' + '' + (1 / #input[0])", "'left' + '' + (1 / #input[0])", "'aggregate' + '' + (1 / #input[1])")

  }

}
