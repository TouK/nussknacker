package pl.touk.nussknacker.engine.flink.util.transformer

import cats.data.NonEmptyList
import com.typesafe.config.ConfigFactory
import eu.timepit.refined.auto._
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.util.Collector
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api
import pl.touk.nussknacker.engine.api.{Context, CustomStreamTransformer, MethodToInvoke, ValueWithContext}
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentOutput}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkMultiOutputStreamTransformation}
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.{MultiOutputSplit, MultiOutputSplitOddEvenInt}
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.{ValidatedValuesDetailedMessage, VeryPatientScalaFutures}

class MultiOutputCustomNodeTest
    extends AnyFunSuite
    with FlinkSpec
    with Matchers
    with OptionValues
    with ValidatedValuesDetailedMessage
    with VeryPatientScalaFutures {

  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private lazy val runner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
    .withExtraComponents(
      List(
        ComponentDefinition("splitOddEven", MultiOutputSplitOddEvenInt),
        ComponentDefinition("splitByMod3", MultiOutputCustomNodeTest.SplitByMod3Transformer),
        ComponentDefinition("splitOddEvenReversed", MultiOutputCustomNodeTest.SplitOddEvenReversedTransformer),
        ComponentDefinition("splitOddEvenWithMainValue", MultiOutputCustomNodeTest.SplitOddEvenWithMainValueTransformer)
      )
    )
    .build()

  private val data = List(1, 2, 3, 4)

  // Both outputs wired and each delivering its own elements is covered by DeduplicationTransformerTest on the real
  // component. What the cases here add is what no real component expresses: a dead-end main output, more declared
  // outputs than the scenario connects, and an output variable.
  test("runs when only the additional output is connected (main output is a dead end)") {
    val scenario = ScenarioBuilder
      .streaming("multi-output-only-rejected")
      .source("start", TestScenarioRunner.testDataSource)
      .customNodeWithOutputs(
        "split",
        None,
        "splitOddEven",
        List(
          "rejected" -> GraphBuilder
            .emptySink("rejected-sink", TestScenarioRunner.testResultSink, "value" -> "'rejected:' + #input".spel)
            .value
        )
      )

    val result = runner.runWithData[Int, String](scenario, data)

    result.validValue.successes.sorted shouldBe List("rejected:1", "rejected:3")
  }

  test("supports a component declaring several additional outputs while only a subset is connected") {
    val scenario = ScenarioBuilder
      .streaming("multi-output-subset-connected")
      .source("start", TestScenarioRunner.testDataSource)
      .customNodeWithOutputs(
        "split",
        None,
        "splitByMod3",
        List(
          "main" -> GraphBuilder
            .emptySink("main-sink", TestScenarioRunner.testResultSink, "value" -> "'main:' + #input".spel)
            .value,
          "rejected" -> GraphBuilder
            .emptySink("rejected-sink", TestScenarioRunner.testResultSink, "value" -> "'rejected:' + #input".spel)
            .value
        )
      )

    val result = runner.runWithData[Int, String](scenario, List(1, 2, 3, 4, 5, 6))

    result.validValue.successes.sorted shouldBe List("main:3", "main:6", "rejected:1", "rejected:4")
  }

  // Registration matches the returned streams to the node's outputs by key, so a component returning them in another
  // order than it declares them is still wired output by output.
  test("wires the outputs of a component returning them in the reverse of the declared order") {
    val scenario = ScenarioBuilder
      .streaming("multi-output-reversed")
      .source("start", TestScenarioRunner.testDataSource)
      .customNodeWithOutputs(
        "split",
        None,
        "splitOddEvenReversed",
        List(
          "main" -> GraphBuilder
            .emptySink("main-sink", TestScenarioRunner.testResultSink, "value" -> "'main:' + #input".spel)
            .value,
          "rejected" -> GraphBuilder
            .emptySink("rejected-sink", TestScenarioRunner.testResultSink, "value" -> "'rejected:' + #input".spel)
            .value
        )
      )

    val result = runner.runWithData[Int, String](scenario, data)

    result.validValue.successes.sorted shouldBe List("main:2", "main:4", "rejected:1", "rejected:3")
  }

  test("puts the output variable in scope on every output, holding null where the component emits no value") {
    val scenario = ScenarioBuilder
      .streaming("multi-output-output-var")
      .source("start", TestScenarioRunner.testDataSource)
      .customNodeWithOutputs(
        "split",
        Some("outVar"),
        "splitOddEvenWithMainValue",
        List(
          "main" -> GraphBuilder
            .emptySink("main-sink", TestScenarioRunner.testResultSink, "value" -> "'main:' + #outVar".spel)
            .value,
          "rejected" -> GraphBuilder
            .emptySink(
              "rejected-sink",
              TestScenarioRunner.testResultSink,
              "value" -> "'rejected:' + #input + ':' + #outVar".spel
            )
            .value
        )
      )

    val result = runner.runWithData[Int, String](scenario, data)

    result.validValue.successes.sorted shouldBe List("main:2", "main:4", "rejected:1:null", "rejected:3:null")
  }

}

object MultiOutputCustomNodeTest {

  object SplitOddEvenReversedTransformer
      extends MultiOutputSplit(List(ComponentOutput.RejectedOutput), returnOutputsReversed = true) {

    override def routeBy(element: api.Context): Option[ComponentOutput] =
      Option.when(element.apply[Int]("input") % 2 != 0)(ComponentOutput.RejectedOutput)

  }

  private val DroppedOutput = ComponentOutput("dropped")

  /** Declares two outputs while the scenarios below connect one, so an unconnected output's elements get dropped. */
  object SplitByMod3Transformer extends MultiOutputSplit(List(ComponentOutput.RejectedOutput, DroppedOutput)) {

    override def routeBy(element: api.Context): Option[ComponentOutput] =
      element.apply[Int]("input") % 3 match {
        case 0 => None
        case 1 => Some(ComponentOutput.RejectedOutput)
        case _ => Some(DroppedOutput)
      }

  }

  /** The asymmetric case: a value to report on the main output (the input int), nothing on the rejected one. */
  object SplitOddEvenWithMainValueTransformer extends CustomStreamTransformer with Serializable {

    override def outputs: NonEmptyList[ComponentOutput] =
      NonEmptyList(ComponentOutput.MainOutput, List(ComponentOutput.RejectedOutput))

    @MethodToInvoke(returnType = classOf[java.lang.Integer])
    def execute(): FlinkMultiOutputStreamTransformation =
      FlinkMultiOutputStreamTransformation { (start: DataStream[Context], ctx: FlinkCustomNodeContext) =>
        val outputTypeInfo = ctx.valueWithContextInfo.forUnknown
        val rejectedTag    = ctx.createOutputTag(ComponentOutput.RejectedOutput, outputTypeInfo)

        val split = start.process(
          new ProcessFunction[api.Context, ValueWithContext[AnyRef]] {
            override def processElement(
                element: api.Context,
                flinkContext: ProcessFunction[api.Context, ValueWithContext[AnyRef]]#Context,
                out: Collector[ValueWithContext[AnyRef]]
            ): Unit = {
              val input = element.apply[Int]("input")
              if (input % 2 == 0) out.collect(ValueWithContext[AnyRef](input.asInstanceOf[AnyRef], element))
              else flinkContext.output(rejectedTag, ValueWithContext[AnyRef](null, element))
            }
          },
          outputTypeInfo
        )

        NonEmptyList.of(
          ComponentOutput.MainOutput     -> split,
          ComponentOutput.RejectedOutput -> split.getSideOutput(rejectedTag)
        )
      }

  }

}
