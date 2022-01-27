package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.flink.streaming.api.scala._
import org.scalatest.{FunSuite, Inside, Matchers}
import pl.touk.nussknacker.engine.api.deployment.{DeploymentData, TestProcess}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.{ProcessListener, ProcessVersion}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.source.EmitWatermarkAfterEachElementCollectionSource
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, ResultsCollectingListenerHolder}
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import collection.JavaConverters._

import java.time.Duration

class ForEachTransformerSpec extends FunSuite with FlinkSpec with Matchers with Inside {

  private val sinkId = "end"
  private val resultVariableName = "resultVar"
  private val forEachOutputVariableName = "forEachVar"

  test("should produce results for each element in list") {
    val model = modelData(List(TestRecord()))

    val testProcess = aProcessWithForEachNode(elements = "{'one', 'other'}", resultExpression = s"#$forEachOutputVariableName + '_1'")

    val results = collectTestResults[String](model, testProcess)
    extractResultValues(results) shouldBe List("one_1", "other_1")
  }

  test("should not produce any results when elements list is empty") {
    val model = modelData(List(TestRecord()))

    val testProcess = aProcessWithForEachNode(elements = "{}")

    val results = collectTestResults[String](model, testProcess)
    results.nodeResults.asJava shouldNot contain key sinkId
  }

  def modelData(list: List[TestRecord] = List()): LocalModelData = LocalModelData(ConfigFactory
    .empty().withValue("useTypingResultTypeInformation", fromAnyRef(true)), new Creator(list))

  private def aProcessWithForEachNode(elements: String, resultExpression: String = s"#$forEachOutputVariableName") =
    EspProcessBuilder
      .id("forEachProcess")
      .parallelism(1)
      .stateOnDisk(true)
      .source("start", "start")
      .buildSimpleVariable("id", "id", "#input.id")
      .customNode("for-each", forEachOutputVariableName, "for-each", "Elements" -> elements)
      .buildSimpleVariable("for-each-result", "resultVar", resultExpression)
      .emptySink(sinkId, "dead-end")

  private def collectTestResults[T](model: LocalModelData, testProcess: EspProcess): TestProcess.TestResults[Any] = {
    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)
    runProcess(model, testProcess, collectingListener)
    collectingListener.results[Any]
  }

  private def extractResultValues(results: TestProcess.TestResults[Any]): List[String] = results.nodeResults(sinkId)
    .map(_.variableTyped[String](resultVariableName).get)

  private def runProcess(model: LocalModelData, testProcess: EspProcess, collectingListener: ResultsCollectingListener): Unit = {
    val stoppableEnv = flinkMiniCluster.createExecutionEnvironment()
    val registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(model) {
      override protected def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] =
        List(collectingListener) ++ super.listeners(processObjectDependencies)
    }, ExecutionConfigPreparer.unOptimizedChain(model))
    registrar.register(new StreamExecutionEnvironment(stoppableEnv), testProcess, ProcessVersion.empty, DeploymentData.empty)
    stoppableEnv.executeAndWaitForFinished(testProcess.id)()
  }

}

class Creator(input: List[TestRecord]) extends EmptyProcessConfigCreator {

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] =
    Map(
      "start" -> WithCategories(SourceFactory.noParam[TestRecord](EmitWatermarkAfterEachElementCollectionSource
        .create[TestRecord](input, _.timestamp, Duration.ofHours(1))))
    )

}

case class TestRecord(id: String = "1", timeHours: Int = 0, eId: Int = 1, str: String = "a") {
  def timestamp: Long = timeHours * 3600L * 1000
}
