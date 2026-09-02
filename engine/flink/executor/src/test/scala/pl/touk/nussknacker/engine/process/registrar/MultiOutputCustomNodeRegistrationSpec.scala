package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.api.java.typeutils.runtime.kryo.KryoSerializer
import org.scalatest.{LoneElement, OptionValues}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar._
import pl.touk.nussknacker.engine.spel.SpelExtension._

import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.jdk.CollectionConverters._

class MultiOutputCustomNodeRegistrationSpec extends FlinkStreamGraphSpec with LoneElement with OptionValues {

  // Inlined `ConfigConstants.METRICS_OPERATOR_NAME_MAX_LENGTH`: the constant is not public in Flink 1.20.
  private val FlinkMetricsOperatorNameMaxLength = 80

  // A BranchEndDefinition is built from the source node alone, so any two outputs of one node reaching the same join
  // produce identical ones, and the registrar merges branch ends into a Map keyed by them. Such a scenario must never
  // reach it, so compilation rejects it first.
  test("should reject a scenario where main output and an additional output feed the same join") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .sources(
        GraphBuilder
          .source("source1", "input")
          .customNodeWithOutputs(
            "multi1",
            None,
            "multiOutputSplitOddEven",
            List(
              "main"     -> GraphBuilder.branchEnd("multi1", "join1").value,
              "rejected" -> GraphBuilder.branchEnd("multi1", "join1").value
            )
          ),
        GraphBuilder
          .join("join1", "sampleJoin", Some("joinInput"), Nil)
          .emptySink("out", "monitor")
      )

    val exception = the[IllegalArgumentException] thrownBy streamGraph(scenario)

    exception.getMessage should include(ProcessCompilationError.MultipleOutputsToSameJoin(Set("multi1")).toString)
  }

  // Both interpretations are registered for the very same splitted node, so without a dedicated name the operators
  // would be indistinguishable in the Flink UI, in logs and in operator-name-scoped metrics.
  test("should give the additional output interpretation an operator name distinct from the main one") {
    val scenarioName = ProcessName("test")
    val scenario = ScenarioBuilder
      .streaming(scenarioName.value)
      .source("source1", "input")
      .customNodeWithOutputs(
        "multi1",
        None,
        "multiOutputSplitOddEven",
        List(
          "main" -> GraphBuilder.emptySink("out", "monitor").get,
          "rejected" -> GraphBuilder
            .buildSimpleVariable("rejVar", "rv", "#input.value1".spel)
            .emptySink("rejected-out", "monitor")
            .get
        )
      )

    val customNodeInterpretationNames = streamGraph(scenario).getStreamNodes.asScala
      .map(_.getOperatorName)
      .filter(_.contains(CustomNodeInterpretationName))
      .toList

    val mainName = interpretationOperatorName(
      scenarioName,
      "multi1",
      CustomNodeInterpretationName,
      shouldUseAsyncInterpretation = false
    )
    val additionalOutputName = interpretationOperatorName(
      scenarioName,
      "$output-rejected-multi1",
      CustomNodeInterpretationName,
      shouldUseAsyncInterpretation = false
    )
    customNodeInterpretationNames should contain theSameElementsAs List(mainName, additionalOutputName)
    additionalOutputName should include("rejected")
  }

  // Why the marker has to lead is explained at its construction site in FlinkProcessRegistrar.
  test("should keep the additional output operator name distinct from the main one within Flink's metric name limit") {
    val scenarioName = ProcessName("a-scenario-name-long-enough-to-be-truncated-in-metrics")
    val nodeId       = UUID.nameUUIDFromBytes("multi1".getBytes(StandardCharsets.UTF_8)).toString
    val scenario = ScenarioBuilder
      .streaming(scenarioName.value)
      .source("source1", "input")
      .customNodeWithOutputs(
        nodeId,
        None,
        "multiOutputSplitOddEven",
        List(
          "main"     -> GraphBuilder.emptySink("out", "monitor").get,
          "rejected" -> GraphBuilder.emptySink("rejected-out", "monitor").get
        )
      )

    val truncatedNames = streamGraph(scenario).getStreamNodes.asScala
      .map(_.getOperatorName)
      .filter(_.contains(CustomNodeInterpretationName))
      .map(_.take(FlinkMetricsOperatorNameMaxLength))
      .toList

    truncatedNames should have size 2
    truncatedNames.distinct should have size 2
  }

  // An additional output arrives typed for an unknown value, so the registrar retypes it to the node's output context.
  test("should type an additional output stream's context exactly like the main one") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source1", "input")
      .customNodeWithOutputs(
        "multi1",
        None,
        "multiOutputSplitOddEven",
        List(
          "main"     -> GraphBuilder.emptySink("out", "monitor").get,
          "rejected" -> GraphBuilder.emptySink("rejected-out", "monitor").get
        )
      )

    val interpretations = streamGraph(scenario).getStreamNodes.asScala
      .filter(_.getOperatorName.contains(CustomNodeInterpretationName))
    val additionalOutputInterpretation = interpretations.find(_.getOperatorName.contains("$output-rejected")).value
    val mainInterpretation             = interpretations.find(!_.getOperatorName.contains("$output-")).value

    val additionalOutputSerializer = additionalOutputInterpretation.getTypeSerializersIn.toList.loneElement
    additionalOutputSerializer should not be a[KryoSerializer[_]]
    // Both serializers are the same class (both are case classes), so what they build is what distinguishes them.
    additionalOutputSerializer.createInstance() shouldBe a[Context]
    additionalOutputSerializer.getClass shouldBe mainInterpretation.getTypeSerializersIn.toList.loneElement.getClass
  }

  // Matching returned streams against the node's outputs must not turn a declaration the scenario ignores into a
  // registration failure: the component cannot know what is connected, so it returns everything it declares.
  test("should register a node whose declared additional output the scenario leaves unconnected") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source1", "input")
      .customNodeNoOutput("multi1", "multiOutputSplitOddEven")
      .emptySink("out", "monitor")

    noException should be thrownBy streamGraph(scenario)
  }

  test("should fail registration when the transformation returns no stream for a connected output") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source1", "input")
      .customNodeWithOutputs(
        "multi1",
        None,
        "multiOutputMissingOutputStream",
        List(
          "main"     -> GraphBuilder.emptySink("out", "monitor").get,
          "rejected" -> GraphBuilder.emptySink("rejected-out", "monitor").get
        )
      )

    val exception = the[IllegalArgumentException] thrownBy streamGraph(scenario)

    exception.getMessage should include("multi1")
    exception.getMessage should include("no stream for connected additional output 'rejected'")
    exception.getMessage should include("It returned [main]")
  }

  // A key matching no declared output leaves the declared one without a stream. Nothing catches it earlier: the keys
  // exist only once the transformation has run, which happens here.
  test("should fail registration when a named main output is returned keyed as MainOutput") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source1", "input")
      .customNodeWithOutputs(
        "multi1",
        None,
        "multiOutputWrongMainOutputKey",
        List(
          "accepted" -> GraphBuilder.emptySink("out", "monitor").get,
          "rejected" -> GraphBuilder.emptySink("rejected-out", "monitor").get
        )
      )

    val exception = the[IllegalArgumentException] thrownBy streamGraph(scenario)

    exception.getMessage should include("no stream for main output 'accepted'")
    exception.getMessage should include("It returned [main, rejected]")
  }

  // A single-output transformation carries no `SupportsMultipleOutputs` marker, so the compiler rejects the scenario
  // instead of letting the deployment fail on a stream that was never returned.
  test("should reject at compilation a connected additional output served by a single-output transformation") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source1", "input")
      .customNodeWithOutputs(
        "multi1",
        None,
        "multiOutputWithSingleOutputTransformation",
        List(
          "main"     -> GraphBuilder.emptySink("out", "monitor").get,
          "rejected" -> GraphBuilder.emptySink("rejected-out", "monitor").get
        )
      )

    val exception = the[IllegalArgumentException] thrownBy streamGraph(scenario)

    exception.getMessage should include("CustomNodeError(multi1,")
    exception.getMessage should include("Additional outputs of this component are not supported on this engine")
  }

  // Only a connected output is rejected above, so the same component still serves a scenario that ignores what it
  // declares - and there the single-output transformation is all that is registered.
  test("should register a single-output transformation whose component declares an unconnected additional output") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source1", "input")
      .customNodeNoOutput("multi1", "multiOutputWithSingleOutputTransformation")
      .emptySink("out", "monitor")

    noException should be thrownBy streamGraph(scenario)
  }

  test("should register a single-output transformation of a component that renamed its main output") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source1", "input")
      .customNodeNoOutput("single1", "singleOutputNamedMainOutput")
      .emptySink("out", "monitor")

    noException should be thrownBy streamGraph(scenario)
  }

  test("should fail registration on a duplicated returned output key") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source1", "input")
      .customNodeWithOutputs(
        "multi1",
        None,
        "multiOutputDuplicatedKey",
        List(
          "main"     -> GraphBuilder.emptySink("out", "monitor").get,
          "rejected" -> GraphBuilder.emptySink("rejected-out", "monitor").get
        )
      )

    val exception = the[IllegalArgumentException] thrownBy streamGraph(scenario)

    exception.getMessage should include("multi1")
    exception.getMessage should include("returned 2 streams for output 'rejected'")
    exception.getMessage should include("bug in the component")
  }

  test("should fail registration on a duplicated key even when that output is not wired") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source1", "input")
      .customNodeWithOutputs(
        "multi1",
        None,
        "multiOutputDuplicatedKey",
        List(
          "main" -> GraphBuilder.emptySink("out", "monitor").get
        )
      )

    val exception = the[IllegalArgumentException] thrownBy streamGraph(scenario)

    exception.getMessage should include("multi1")
    exception.getMessage should include("returned 2 streams for output 'rejected'")
    exception.getMessage should include("bug in the component")
  }

}
