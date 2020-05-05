package pl.touk.nussknacker.engine.process

import cats.data.NonEmptyList
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.graph.{StreamGraph, StreamNode}
import org.apache.flink.streaming.api.operators.async.AsyncWaitOperator
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{FunSuite, Matchers, OptionValues}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process.{ProcessConfigCreator, ProcessName}
import pl.touk.nussknacker.engine.build.GraphBuilder
import pl.touk.nussknacker.engine.flink.api.NkGlobalParameters
import pl.touk.nussknacker.engine.flink.test.StoppableExecutionEnvironment
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers.processInvoker
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.collection.JavaConverters._

class FlinkStreamingProcessRegistrarOperatorUidSpec extends FunSuite with Matchers with OptionValues with PatientScalaFutures {

  import pl.touk.nussknacker.engine.spel.Implicits._

  test("should set uid for source and sink") {
    val sourceId = "sourceId"
    val sinkId = "sinkId"
    val process = EspProcess(MetaData("proc1", StreamMetaData()),
      ExceptionHandlerRef(List.empty),
      NonEmptyList.of(GraphBuilder.source(sourceId, "input").emptySink(sinkId, "monitor")))

    val graph = streamGraph(process)
    graph.firstSource.getTransformationUID shouldEqual sourceId
    graph.sinks.exists(_.getTransformationUID == sinkId) shouldBe true
  }

  test("should set uid for async functions") {
    val process = EspProcess(MetaData("proc1",
      StreamMetaData(useAsyncInterpretation = Some(true))),
      ExceptionHandlerRef(List.empty),
      NonEmptyList.of(GraphBuilder
        .source("sourceId", "input")
        .processor("processorId", "logService", "all" -> "123")
        .emptySink("sinkId", "monitor")))

    val graph = streamGraph(process)
    val sourceNode = graph.firstSource
    val asyncOperators = graph.traverse(sourceNode).filter(_.getOperator.isInstanceOf[AsyncWaitOperator[_, _]]).toList
    val asyncOperatorUids = asyncOperators.map(o => Option(o.getTransformationUID))
    asyncOperatorUids.forall(_.value.endsWith("-$async")) shouldBe true
  }

  test("should set uid for custom stateful function") {
    val customNodeId = "customNodeId"
    val process = EspProcess(MetaData("proc1", StreamMetaData()),
      ExceptionHandlerRef(List.empty),
      NonEmptyList.of(GraphBuilder
        .source("sourceId", "input")
        .customNode(customNodeId, "out", "stateCustom", "stringVal" -> "'123'", "keyBy" -> "'123'")
        .emptySink("sinkId", "monitor")))

    val graph = streamGraph(process)
    val sourceNode = graph.firstSource
    val stateOperatorList = graph.traverse(sourceNode).filter(_.getStateKeySerializer != null).toList
    stateOperatorList should have length 1
    stateOperatorList.head.getTransformationUID shouldEqual customNodeId
  }

  def streamGraph(process: EspProcess): StreamGraph = {
    val config = ConfigFactory.load()
      .withValue("kafka.kafkaAddress", fromAnyRef("http://notexist.pl"))
      .withValue("globalParameters.explicitUidInStatefulOperators", fromAnyRef(true))
    val creator: ProcessConfigCreator = processInvoker.prepareCreator(List.empty, config)

    val env = StoppableExecutionEnvironment(new Configuration())
    NkGlobalParameters.setInContext(env.getConfig, NkGlobalParameters("fooBuildInfo", ProcessVersion(1L, ProcessName("fooProcess"), "fooUser", None), config))
    try {
      FlinkStreamingProcessRegistrar(new FlinkProcessCompiler(LocalModelData(config, creator)), config)
        .register(new StreamExecutionEnvironment(env), process, ProcessVersion.empty)
      env.getStreamGraph
    } finally {
      env.stop()
    }
  }

  implicit class EnahncedStreamGraph(graph: StreamGraph) {

    def firstSource: StreamNode = graph.getStreamNode(graph.getSourceIDs.asScala.toList.head)

    def sinks: List[StreamNode] = graph.getSinkIDs.asScala.map(graph.getStreamNode).toList

    def traverse(node: StreamNode): Stream[StreamNode] =
      node #:: node.getOutEdgeIndices.asScala.toStream.map(graph.getStreamNode).flatMap(traverse)

  }

}
