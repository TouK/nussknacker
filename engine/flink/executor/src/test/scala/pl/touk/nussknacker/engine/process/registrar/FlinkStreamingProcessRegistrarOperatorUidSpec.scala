package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.streaming.api.operators.async.AsyncWaitOperatorFactory
import pl.touk.nussknacker.engine.build.ScenarioBuilder

class FlinkStreamingProcessRegistrarOperatorUidSpec extends FlinkStreamGraphSpec {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  test("should set uid for source and sink") {
    val sourceId = "sourceId"
    val sinkId   = "sinkId"
    val process = ScenarioBuilder
      .streaming("test")
      .source(sourceId, "input")
      .emptySink(sinkId, "monitor")

    val graph = streamGraph(process)
    graph.firstSource.getTransformationUID shouldEqual sourceId
    graph.sinks.exists(_.getTransformationUID == sinkId) shouldBe true
  }

  test("should set uid for async functions") {
    val process = ScenarioBuilder
      .streaming("test")
      .useAsyncInterpretation(true)
      .source("sourceId", "input")
      .processor("processorId", "logService", "all" -> "123".spel)
      .emptySink("sinkId", "monitor")

    val graph      = streamGraph(process)
    val sourceNode = graph.firstSource
    val asyncOperators =
      graph.traverse(sourceNode).filter(_.getOperatorFactory.isInstanceOf[AsyncWaitOperatorFactory[_, _]]).toList
    val asyncOperatorUids = asyncOperators.map(o => Option(o.getTransformationUID))
    asyncOperatorUids.forall(_.value.endsWith("-$async")) shouldBe true
  }

  test("should set uid for custom stateful function") {
    val customNodeId = "customNodeId"
    val process = ScenarioBuilder
      .streaming("test")
      .source("sourceId", "input")
      .customNode(customNodeId, "out", "stateCustom", "stringVal" -> "'123'".spel, "groupBy" -> "'123'".spel)
      .emptySink("sinkId", "monitor")

    val graph             = streamGraph(process)
    val sourceNode        = graph.firstSource
    val stateOperatorList = graph.traverse(sourceNode).filter(_.getStateKeySerializer != null).toList
    stateOperatorList should have length 1
    stateOperatorList.head.getTransformationUID shouldEqual customNodeId
  }

}
