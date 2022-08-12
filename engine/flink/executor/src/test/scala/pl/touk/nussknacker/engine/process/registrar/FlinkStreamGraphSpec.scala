package pl.touk.nussknacker.engine.process.registrar

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.streaming.api.graph.{StreamGraph, StreamNode}
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.collection.JavaConverters._

trait FlinkStreamGraphSpec extends AnyFunSuite with ProcessTestHelpers with Matchers with OptionValues with PatientScalaFutures {

  protected def streamGraph(process: EspProcess,
                            config: Config = ConfigFactory.load()): StreamGraph = {
    val creator: ProcessConfigCreator = ProcessTestHelpers.prepareCreator(List.empty, config)

    val env = flinkMiniCluster.createExecutionEnvironment()
    val modelData = LocalModelData(config, creator)
    FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), ExecutionConfigPreparer.unOptimizedChain(modelData))
      .register(new StreamExecutionEnvironment(env), process, ProcessVersion.empty, DeploymentData.empty)
    env.getStreamGraph
  }

  implicit class EnhancedStreamGraph(graph: StreamGraph) {

    def firstSource: StreamNode = graph.getStreamNode(graph.getSourceIDs.asScala.toList.head)

    def sinks: List[StreamNode] = graph.getSinkIDs.asScala.map(graph.getStreamNode).toList

    def traverse(node: StreamNode): Stream[StreamNode] =
      node #:: node.getOutEdgeIndices.asScala.toStream.map(graph.getStreamNode).flatMap(traverse)

  }
}
