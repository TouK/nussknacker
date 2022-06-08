package pl.touk.nussknacker.engine.flink.util.transformer

import cats.data.NonEmptyList
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.flink.streaming.api.scala._
import org.scalatest.{FunSuite, Inside, Matchers}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{ProcessListener, ProcessVersion}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.compiledgraph.part.PotentiallyStartPart
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.source.EmitWatermarkAfterEachElementCollectionSource
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.{FlinkProcessCompiler, UsedNodes}
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode._

import java.time.Duration
import java.util.UUID

class ForEachTransformerSpec extends FunSuite with FlinkSpec with Matchers with Inside {

  private val sinkId = "end"
  private val resultVariableName = "resultVar"
  private val forEachOutputVariableName = "forEachVar"
  private val forEachNodeId = "for-each"

  test("should produce results for each element in list") {
    val collectingListener = initializeListener
    val model = modelData(List(TestRecord()), collectingListener)

    val testProcess = aProcessWithForEachNode(elements = "{'one', 'other'}", resultExpression = s"#$forEachOutputVariableName + '_1'")

    val results = collectTestResults[String](model, testProcess, collectingListener)
    extractResultValues(results) shouldBe List("one_1", "other_1")
  }

  test("should set return type based on element types") {
    val collectingListener = initializeListener
    val model = modelData(List(TestRecord()), collectingListener)

    val testProcess = aProcessWithForEachNode(elements = "{'one', 'other'}", resultExpression = s"#$forEachOutputVariableName + '_1'")

    val compiledSources: NonEmptyList[PotentiallyStartPart] = getCompiledSources(model, testProcess)

    val forEachOutputVariable = compiledSources.head.nextParts.find(_.id == forEachNodeId)
      .get.validationContext.variables(forEachOutputVariableName)

    forEachOutputVariable shouldBe Typed[String]
  }

  test("should not produce any results when elements list is empty") {
    val collectingListener = initializeListener
    val model = modelData(List(TestRecord()), collectingListener)

    val testProcess = aProcessWithForEachNode(elements = "{}")

    val results = collectTestResults[String](model, testProcess, collectingListener)
    results.nodeResults shouldNot contain key sinkId
  }

  private def initializeListener = ResultsCollectingListenerHolder.registerRun(identity)

  private def modelData(list: List[TestRecord] = List(), collectingListener: ResultsCollectingListener): LocalModelData = LocalModelData(ConfigFactory
    .empty().withValue("useTypingResultTypeInformation", fromAnyRef(true)), new Creator(list, collectingListener))

  private def aProcessWithForEachNode(elements: String, resultExpression: String = s"#$forEachOutputVariableName") =
    ScenarioBuilder
      .streaming("forEachProcess")
      .parallelism(1)
      .stateOnDisk(true)
      .source("start", "start")
      .customNode(forEachNodeId, forEachOutputVariableName, "for-each", "Elements" -> elements)
      .buildSimpleVariable("for-each-result", "resultVar", resultExpression)
      .emptySink(sinkId, "dead-end")

  private def collectTestResults[T](model: LocalModelData, testProcess: EspProcess, collectingListener: ResultsCollectingListener): TestProcess.TestResults[Any] = {
    runProcess(model, testProcess)
    collectingListener.results[Any]
  }

  private def extractResultValues(results: TestProcess.TestResults[Any]): List[String] = results.nodeResults(sinkId)
    .map(_.variableTyped[String](resultVariableName).get)

  private def getCompiledSources(model: LocalModelData, testProcess: EspProcess): NonEmptyList[PotentiallyStartPart] = {
    val compiler = new FlinkProcessCompiler(model)
    val compiledSources = compiler.compileProcess(testProcess, ProcessVersion.empty, DeploymentData.empty, new TestServiceInvocationCollector(TestRunId(UUID.randomUUID().toString)))(UsedNodes.empty, getClass.getClassLoader)
      .compileProcess()
      .sources
    compiledSources
  }

  private def runProcess(model: LocalModelData, testProcess: EspProcess): Unit = {
    val stoppableEnv = flinkMiniCluster.createExecutionEnvironment()
    val registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(model), ExecutionConfigPreparer.unOptimizedChain(model))
    registrar.register(new StreamExecutionEnvironment(stoppableEnv), testProcess, ProcessVersion.empty, DeploymentData.empty)
    stoppableEnv.executeAndWaitForFinished(testProcess.id)()
  }
}

class Creator(input: List[TestRecord], collectingListener: ResultsCollectingListener) extends EmptyProcessConfigCreator {

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] =
    Map(
      "start" -> WithCategories(SourceFactory.noParam[TestRecord](EmitWatermarkAfterEachElementCollectionSource
        .create[TestRecord](input, _.timestamp, Duration.ofHours(1))))
    )

  override def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] =
    List(collectingListener)
}

case class TestRecord(id: String = "1", timeHours: Int = 0, eId: Int = 1, str: String = "a") {
  def timestamp: Long = timeHours * 3600L * 1000
}
