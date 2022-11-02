package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{ProcessListener, ProcessVersion}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.source.EmitWatermarkAfterEachElementCollectionSource
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode._

import java.time.Duration

class ForEachTransformerSpec extends AnyFunSuite with FlinkSpec with Matchers with Inside {

  private val sinkId = "end"
  private val resultVariableName = "resultVar"
  private val forEachOutputVariableName = "forEachVar"
  private val forEachNodeResultId = "for-each-result"

  test("should produce results for each element in list") {
    val collectingListener = initializeListener
    val model = modelData(List(TestRecord()), collectingListener)

    val testProcess = aProcessWithForEachNode(elements = "{'one', 'other'}", resultExpression = s"#$forEachOutputVariableName + '_1'")

    val results = collectTestResults[String](model, testProcess, collectingListener)
    extractResultValues(results) shouldBe List("one_1", "other_1")
  }

  test("should produce unique contextId for each element in list") {
    val collectingListener = initializeListener
    val model = modelData(List(TestRecord()), collectingListener)

    val testProcess = aProcessWithForEachNode(elements = "{'one', 'other'}", resultExpression = s"#$forEachOutputVariableName + '_1'")

    val results = collectTestResults[String](model, testProcess, collectingListener)
    extractContextIds(results) shouldBe List("forEachProcess-start-0-0-0", "forEachProcess-start-0-0-1")
  }

  test("should set return type based on element types") {
    val collectingListener = initializeListener
    val model = modelData(List(TestRecord()), collectingListener)

    val testProcess = aProcessWithForEachNode(elements = "{'one', 'other'}", resultExpression = s"#$forEachOutputVariableName + '_1'")
    val processValidator = model.prepareValidatorForCategory(None)

    val forEachResultValidationContext = processValidator.validate(testProcess).typing(forEachNodeResultId)
    forEachResultValidationContext.inputValidationContext.get(forEachOutputVariableName) shouldBe Some(Typed[String])
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
      .customNode("for-each", forEachOutputVariableName, "for-each", "Elements" -> elements)
      .buildSimpleVariable(forEachNodeResultId, "resultVar", resultExpression)
      .emptySink(sinkId, "dead-end")

  private def collectTestResults[T](model: LocalModelData, testProcess: CanonicalProcess, collectingListener: ResultsCollectingListener): TestProcess.TestResults[Any] = {
    runProcess(model, testProcess)
    collectingListener.results[Any]
  }

  private def extractResultValues(results: TestProcess.TestResults[Any]): List[String] = results.nodeResults(sinkId)
    .map(_.variableTyped[String](resultVariableName).get)

  private def extractContextIds(results: TestProcess.TestResults[Any]): List[String] = results.nodeResults(forEachNodeResultId)
    .map(_.context.id)

  private def runProcess(model: LocalModelData, testProcess: CanonicalProcess): Unit = {
    val stoppableEnv = flinkMiniCluster.createExecutionEnvironment()
    val registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(model), ExecutionConfigPreparer.unOptimizedChain(model))
    registrar.register(stoppableEnv, testProcess, ProcessVersion.empty, DeploymentData.empty)
    stoppableEnv.executeAndWaitForFinished(testProcess.id)()
  }
}

class Creator(input: List[TestRecord], collectingListener: ResultsCollectingListener) extends EmptyProcessConfigCreator {

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] =
    Map(
      "start" -> WithCategories(SourceFactory.noParam[TestRecord](EmitWatermarkAfterEachElementCollectionSource
        .create[TestRecord](input, _.timestamp, Duration.ofHours(1))(TypeInformation.of(classOf[TestRecord]))))
    )

  override def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] =
    List(collectingListener)
}

case class TestRecord(id: String = "1", timeHours: Int = 0, eId: Int = 1, str: String = "a") {
  def timestamp: Long = timeHours * 3600L * 1000
}
