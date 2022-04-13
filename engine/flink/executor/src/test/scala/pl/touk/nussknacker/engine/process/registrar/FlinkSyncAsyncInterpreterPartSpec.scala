package pl.touk.nussknacker.engine.process.registrar

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.streaming.api.graph.{StreamGraph, StreamNode}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar.{BranchInterpretationName, CustomNodeInterpretationName, InterpretationName}
import pl.touk.nussknacker.engine.spel.Implicits.asSpelExpression

import scala.collection.JavaConverters._

class FlinkSyncAsyncInterpreterPartSpec extends FlinkStreamGraphSpec {

  private val scenarioId = "test"
  private val interpretationNodeNames = Set(InterpretationName, CustomNodeInterpretationName, BranchInterpretationName)

  test("should always use sync interpretation if explicitly set") {
    val graph = streamGraph(prepareProcess(useAsyncInterpretation = false))

    val interpretationNodes = getInterpretationNodes(graph)
    interpretationNodes should have size 5 // TODO should be 4
    every(interpretationNodes.map(_.getOperatorName)) should endWith("Sync")
  }

  test("should always use async interpretation if explicitly set") {
    val graph = streamGraph(prepareProcess(useAsyncInterpretation = true))

    val interpretationNodes = getInterpretationNodes(graph)
    interpretationNodes should have size 5 // TODO should be 4
    every(interpretationNodes.map(_.getOperatorName)) should endWith("Async")
  }

  test("should use sync interpretation with async enabled for part that does not contain services - detect sync part flag enabled") {
    val graph = streamGraph(prepareProcess(useAsyncInterpretation = true), config = prepareConfig(detectSyncPart = Some(true)))

    val interpretationNodes = getInterpretationNodes(graph)
    interpretationNodes should have size 5 // TODO should be 4
    val operatorNames = interpretationNodes.map(_.getOperatorName)
    exactly(3, operatorNames) should endWith("Async")
    exactly(1, operatorNames) should endWith("Sync")
    operatorNames should contain allOf("a", "b", "c")
    operatorNames should contain ("d")
  }

  private def getInterpretationNodes(graph: StreamGraph): Iterable[StreamNode] = {
    graph.getStreamNodes.asScala.filter(node => interpretationNodeNames.exists(node.getOperatorName.contains))
  }

  private def prepareProcess(useAsyncInterpretation: Boolean) = ScenarioBuilder
    .streaming(scenarioId)
    .useAsyncInterpretation(useAsyncInterpretation)
    // Source part contains services.
    // TODO: change to join.
    .source("sourceId", "input")
    .enricher("enricherId", "outputValue", "enricherWithOpenService")
    .filter("filterId", "#input.value1 > 1")
    .processor("processorId", "logService", "all" -> "123")
    .buildSimpleVariable("varId", "var1", "42L")
    .split("split",
      // Only sink.
      GraphBuilder
        .customNode("customId2", "customOutput2", "stateCustom", "groupBy" -> "''", "stringVal" -> "''")
        .emptySink("sinkId2", "monitor"),
      // Only sync components.
      GraphBuilder
        .customNode("customId3", "customOutput3", "stateCustom", "groupBy" -> "''", "stringVal" -> "''")
        .buildSimpleVariable("varId3", "var3", "'xyz'")
        .filter("filterId3", "#input.value1 > 1")
        .emptySink("sinkId3", "monitor"),
      // Contains a service.
      GraphBuilder
        .customNode("customId4", "customOutput4", "stateCustom", "groupBy" -> "''", "stringVal" -> "''")
        .processorEnd("processorId4", "logService", "all" -> "123"),
      // Contains a service.
      GraphBuilder
        .customNode("customId5", "customOutput5", "stateCustom", "groupBy" -> "''", "stringVal" -> "''")
        .buildSimpleVariable("varId5", "var5", "'XYZ'")
        .enricher("enricherId5", "outputValue5", "enricherWithOpenService")
        .processorEnd("processorId5", "logService", "all" -> "123"),
      // Only ending custom node.
      GraphBuilder
        .endingCustomNode("customEnding6", None, "optionalEndingCustom", "param" -> "'param'"),
      // Only ending sink.
      GraphBuilder
        .emptySink("sinkId7", "monitor"),
    )

  private def prepareConfig(detectSyncPart: Option[Boolean] = None): Config = {
    val baseConfig = ConfigFactory.load()
    detectSyncPart
      .map(v => baseConfig.withValue("globalParameters.detectSyncPart", fromAnyRef(v)))
      .getOrElse(baseConfig)
  }

}
