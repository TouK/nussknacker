package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.scalatest.FunSuite
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.CustomStreamTransformer
import pl.touk.nussknacker.engine.api.process.{ExpressionConfig, ProcessObjectDependencies, WithCategories}
import pl.touk.nussknacker.engine.build.GraphBuilder
import pl.touk.nussknacker.engine.flink.test.{CorrectExceptionHandlingSpec, FlinkSpec, MiniClusterExecutionEnvironment}
import pl.touk.nussknacker.engine.flink.util.transformer.{DelayTransformer, PreviousValueTransformer, UnionTransformer, UnionWithMemoTransformer}
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.sampleTransformers.{SessionWindowAggregateTransformer, SlidingAggregateTransformerV2, TumblingAggregateTransformer}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.runner.TestFlinkRunner
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.spel.Implicits._

class ModelUtilExceptionHandlingSpec extends FunSuite with CorrectExceptionHandlingSpec with FlinkSpec {

  override protected def registerInEnvironment(env: MiniClusterExecutionEnvironment, modelData: ModelData, scenario: EspProcess): Unit
    = TestFlinkRunner.registerInEnvironmentWithModel(env, modelData)(scenario)

  test("should handle exceptions in aggregate keys") {

    val configCreator = new EmptyProcessConfigCreator() {
      override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] =
        Map(
          "previousValue" -> WithCategories(PreviousValueTransformer),
          "aggregate-sliding" -> WithCategories(SlidingAggregateTransformerV2),
          "aggregate-tumbling" -> WithCategories(TumblingAggregateTransformer),
          "aggregate-session" -> WithCategories(SessionWindowAggregateTransformer),
          //FIXME: tests for joins...
          //"single-side-join" -> WithCategories(SingleSideJoinTransformer),
          "union" -> WithCategories(UnionTransformer),
          "union-memo" -> WithCategories(UnionWithMemoTransformer),
          "delay" -> WithCategories(DelayTransformer)
        )

      override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig
      = super.expressionConfig(processObjectDependencies).copy(globalProcessVariables = Map("AGG" -> WithCategories(new AggregateHelper)))

    }

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
          "windowLength" -> "T(java.time.Duration).parse('PT1M')",
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
            "windowLength" -> "T(java.time.Duration).parse('PT1M')",
            "emitWhen" -> "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.TumblingWindowTrigger).OnEvent"
          ).emptySink("end", "empty"),
          GraphBuilder.customNode("aggregate-session", "out3", "aggregate-session",
            "groupBy" -> generator.throwFromString(),
            "aggregateBy" -> generator.throwFromString(),
            "aggregator" -> "#AGG.first",
            "sessionTimeout" -> "T(java.time.Duration).parse('PT1M')",
            "endSessionCondition" -> "true",
            "emitWhen" -> "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.SessionWindowTrigger).OnEvent"
          ).emptySink("end2", "empty")


        )

    }
  }

}
