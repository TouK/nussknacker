package pl.touk.nussknacker.engine.process.registrar

import cats.data.NonEmptyList
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.streaming.api.graph.{StreamGraph, StreamNode}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.build.GraphBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar.{BranchInterpretationName, CustomNodeInterpretationName, InterpretationName, SinkInterpretationName, interpretationOperatorName}
import pl.touk.nussknacker.engine.spel.Implicits.asSpelExpression

import scala.collection.JavaConverters._

class InterpretationFunctionFlinkGraphSpec extends FlinkStreamGraphSpec {

  private val scenarioId = "test"
  private val interpretationNodeNames = Set(InterpretationName, CustomNodeInterpretationName, BranchInterpretationName, SinkInterpretationName)

  test("should always use sync interpretation if explicitly set") {
    val graph = streamGraph(prepareProcess(useAsyncInterpretation = false))

    val interpretationNodes = getInterpretationNodes(graph)
    interpretationNodes should have size 11
    every(interpretationNodes.map(_.getOperatorName)) should endWith("Sync")
  }

  test("should always use async interpretation if explicitly set - force sync interpretation disabled") {
    val graph = streamGraph(prepareProcess(useAsyncInterpretation = true), config = prepareConfig(forceSyncInterpretationForSyncScenarioPart = Some(false)))

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
    operatorNames should contain only(
      interpretationOperatorName(scenarioId, "sourceId1", InterpretationName, shouldUseAsyncInterpretation = true),
      interpretationOperatorName(scenarioId, "sourceId2", InterpretationName, shouldUseAsyncInterpretation = false),
      interpretationOperatorName(scenarioId, "joinId", BranchInterpretationName, shouldUseAsyncInterpretation = false),
      interpretationOperatorName(scenarioId, "customId4", CustomNodeInterpretationName, shouldUseAsyncInterpretation = false),
      interpretationOperatorName(scenarioId, "sinkId4", SinkInterpretationName, shouldUseAsyncInterpretation = false),
      interpretationOperatorName(scenarioId, "customId5", CustomNodeInterpretationName, shouldUseAsyncInterpretation = false),
      interpretationOperatorName(scenarioId, "sinkId5", SinkInterpretationName, shouldUseAsyncInterpretation = false),
      interpretationOperatorName(scenarioId, "customId6", CustomNodeInterpretationName, shouldUseAsyncInterpretation = true),
      interpretationOperatorName(scenarioId, "customId7", CustomNodeInterpretationName, shouldUseAsyncInterpretation = true),
      interpretationOperatorName(scenarioId, "customEnding8", CustomNodeInterpretationName, shouldUseAsyncInterpretation = false),
      interpretationOperatorName(scenarioId, "sinkId9", SinkInterpretationName, shouldUseAsyncInterpretation = false),
    )
  }

  private def getInterpretationNodes(graph: StreamGraph): Iterable[StreamNode] = {
    graph.getStreamNodes.asScala.filter(node => interpretationNodeNames.exists(node.getOperatorName.contains))
  }

  private def prepareProcess(useAsyncInterpretation: Boolean) = EspProcess(
    MetaData(id = scenarioId, typeSpecificData = StreamMetaData(useAsyncInterpretation = Some(useAsyncInterpretation))),
    NonEmptyList.of(
      GraphBuilder
        // Source part contains services.
        .source("sourceId1", "input")
        .enricher("enricherId1", "outputValue", "enricherWithOpenService")
        .filter("filterId1", "#input.value1 > 1")
        .processor("processorId1", "logService", "all" -> "123")
        .branchEnd("end1", "joinId"),
      GraphBuilder
        // Source part does not contain services.
        .source("sourceId2", "input")
        .buildSimpleVariable("varId2", "var2", "42L")
        .branchEnd("end2", "joinId"),
      GraphBuilder
        .join("joinId", "sampleJoin", Some("joinInput"), Nil)
        .buildSimpleVariable("varId3", "var3", "5L")
        .split("split",
          // Only sink after custom node but interpretation function is needed for metrics etc.
          GraphBuilder
            .customNode("customId4", "customOutput4", "stateCustom", "groupBy" -> "''", "stringVal" -> "''")
            .emptySink("sinkId4", "monitor"),
          // Only sync components.
          GraphBuilder
            .customNode("customId5", "customOutput5", "stateCustom", "groupBy" -> "''", "stringVal" -> "''")
            .buildSimpleVariable("varId5", "var5", "'xyz'")
            .filter("filterId5", "true")
            .emptySink("sinkId5", "monitor"),
          // Contains a service.
          GraphBuilder
            .customNode("customId6", "customOutput6", "stateCustom", "groupBy" -> "''", "stringVal" -> "''")
            .processorEnd("processorId6", "logService", "all" -> "123"),
          // Contains a service.
          GraphBuilder
            .customNode("customId7", "customOutput7", "stateCustom", "groupBy" -> "''", "stringVal" -> "''")
            .buildSimpleVariable("varId7", "var7", "'XYZ'")
            .enricher("enricherId7", "outputValue7", "enricherWithOpenService")
            .processorEnd("processorId7", "logService", "all" -> "123"),
          // Only ending custom node.
          GraphBuilder
            .endingCustomNode("customEnding8", None, "optionalEndingCustom", "param" -> "'param'"),
          // Only ending sink but interpretation function is needed for metrics etc.
          GraphBuilder
            .emptySink("sinkId9", "monitor"),
        ),
    )
  )

  private def prepareConfig(forceSyncInterpretationForSyncScenarioPart: Option[Boolean] = None): Config = {
    val baseConfig = ConfigFactory.load()
    forceSyncInterpretationForSyncScenarioPart
      .map(v => baseConfig.withValue("globalParameters.forceSyncInterpretationForSyncScenarioPart", fromAnyRef(v)))
      .getOrElse(baseConfig)
  }

}
