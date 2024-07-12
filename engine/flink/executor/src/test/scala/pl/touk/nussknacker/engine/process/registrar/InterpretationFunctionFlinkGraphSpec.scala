package pl.touk.nussknacker.engine.process.registrar

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.streaming.api.graph.{StreamGraph, StreamNode}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar._
import pl.touk.nussknacker.engine.spel.SpelExtension._

import scala.jdk.CollectionConverters._

class InterpretationFunctionFlinkGraphSpec extends FlinkStreamGraphSpec {

  private val scenarioName = ProcessName("test")
  private val interpretationNodeNames =
    Set(InterpretationName, CustomNodeInterpretationName, BranchInterpretationName, SinkInterpretationName)

  test("should always use sync interpretation if explicitly set") {
    val graph = streamGraph(prepareProcess(useAsyncInterpretation = false))

    val interpretationNodes = getInterpretationNodes(graph)
    interpretationNodes should have size 11
    every(interpretationNodes.map(_.getOperatorName)) should endWith("Sync")
  }

  test("should always use async interpretation if explicitly set - force sync interpretation disabled") {
    val graph = streamGraph(
      prepareProcess(useAsyncInterpretation = true),
      config = prepareConfig(forceSyncInterpretationForSyncScenarioPart = Some(false))
    )

    val interpretationNodes = getInterpretationNodes(graph)
    interpretationNodes should have size 11
    every(interpretationNodes.map(_.getOperatorName)) should endWith("Async")
  }

  test("should use sync interpretation with async enabled for part that does not contain services") {
    val graph = streamGraph(prepareProcess(useAsyncInterpretation = true))

    val interpretationNodes = getInterpretationNodes(graph)
    interpretationNodes should have size 11
    val operatorNames = interpretationNodes.map(_.getOperatorName)
    exactly(3, operatorNames) should endWith("Async")
    exactly(8, operatorNames) should endWith("Sync")
    operatorNames should contain only (
      interpretationOperatorName(scenarioName, "sourceId1", InterpretationName, shouldUseAsyncInterpretation = true),
      interpretationOperatorName(scenarioName, "sourceId2", InterpretationName, shouldUseAsyncInterpretation = false),
      interpretationOperatorName(
        scenarioName,
        "joinId",
        BranchInterpretationName,
        shouldUseAsyncInterpretation = false
      ),
      interpretationOperatorName(
        scenarioName,
        "customId4",
        CustomNodeInterpretationName,
        shouldUseAsyncInterpretation = false
      ),
      interpretationOperatorName(scenarioName, "sinkId4", SinkInterpretationName, shouldUseAsyncInterpretation = false),
      interpretationOperatorName(
        scenarioName,
        "customId5",
        CustomNodeInterpretationName,
        shouldUseAsyncInterpretation = false
      ),
      interpretationOperatorName(scenarioName, "sinkId5", SinkInterpretationName, shouldUseAsyncInterpretation = false),
      interpretationOperatorName(
        scenarioName,
        "customId6",
        CustomNodeInterpretationName,
        shouldUseAsyncInterpretation = true
      ),
      interpretationOperatorName(
        scenarioName,
        "customId7",
        CustomNodeInterpretationName,
        shouldUseAsyncInterpretation = true
      ),
      interpretationOperatorName(
        scenarioName,
        "customEnding8",
        CustomNodeInterpretationName,
        shouldUseAsyncInterpretation = false
      ),
      interpretationOperatorName(scenarioName, "sinkId9", SinkInterpretationName, shouldUseAsyncInterpretation = false),
    )
  }

  private def getInterpretationNodes(graph: StreamGraph): Iterable[StreamNode] = {
    graph.getStreamNodes.asScala.filter(node => interpretationNodeNames.exists(node.getOperatorName.contains))
  }

  private def prepareProcess(useAsyncInterpretation: Boolean) = ScenarioBuilder
    .streaming(scenarioName.value)
    .useAsyncInterpretation(useAsyncInterpretation)
    .sources(
      GraphBuilder
        // Source part contains services.
        .source("sourceId1", "input")
        .enricher("enricherId1", "outputValue", "enricherWithOpenService")
        .filter("filterId1", "#input.value1 > 1".spel)
        .processor("processorId1", "logService", "all" -> "123".spel)
        .branchEnd("end1", "joinId"),
      GraphBuilder
        // Source part does not contain services.
        .source("sourceId2", "input")
        .buildSimpleVariable("varId2", "var2", "42L".spel)
        .branchEnd("end2", "joinId"),
      GraphBuilder
        .join("joinId", "sampleJoin", Some("joinInput"), Nil)
        .buildSimpleVariable("varId3", "var3", "5L".spel)
        .split(
          "split",
          // Only sink after custom node but interpretation function is needed for metrics etc.
          GraphBuilder
            .customNode("customId4", "customOutput4", "stateCustom", "groupBy" -> "''".spel, "stringVal" -> "''".spel)
            .emptySink("sinkId4", "monitor"),
          // Only sync components.
          GraphBuilder
            .customNode("customId5", "customOutput5", "stateCustom", "groupBy" -> "''".spel, "stringVal" -> "''".spel)
            .buildSimpleVariable("varId5", "var5", "'xyz'".spel)
            .filter("filterId5", "true".spel)
            .emptySink("sinkId5", "monitor"),
          // Contains a service.
          GraphBuilder
            .customNode("customId6", "customOutput6", "stateCustom", "groupBy" -> "''".spel, "stringVal" -> "''".spel)
            .processorEnd("processorId6", "logService", "all" -> "123".spel),
          // Contains a service.
          GraphBuilder
            .customNode("customId7", "customOutput7", "stateCustom", "groupBy" -> "''".spel, "stringVal" -> "''".spel)
            .buildSimpleVariable("varId7", "var7", "'XYZ'".spel)
            .enricher("enricherId7", "outputValue7", "enricherWithOpenService")
            .processorEnd("processorId7", "logService", "all" -> "123".spel),
          // Only ending custom node.
          GraphBuilder
            .endingCustomNode("customEnding8", None, "optionalEndingCustom", "param" -> "'param'".spel),
          // Only ending sink but interpretation function is needed for metrics etc.
          GraphBuilder
            .emptySink("sinkId9", "monitor"),
        ),
    )

  private def prepareConfig(forceSyncInterpretationForSyncScenarioPart: Option[Boolean] = None): Config = {
    val baseConfig = ConfigFactory.load()
    forceSyncInterpretationForSyncScenarioPart
      .map(v => baseConfig.withValue("globalParameters.forceSyncInterpretationForSyncScenarioPart", fromAnyRef(v)))
      .getOrElse(baseConfig)
  }

}
