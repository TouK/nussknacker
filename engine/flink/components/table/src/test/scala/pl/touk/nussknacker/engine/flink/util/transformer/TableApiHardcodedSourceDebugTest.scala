package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.ConfigFactory
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.process.helpers.ConfigCreatorWithCollectingListener
import pl.touk.nussknacker.engine.process.runner.UnitTestsFlinkRunner
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, ResultsCollectingListenerHolder, TestProcess}
import pl.touk.nussknacker.engine.spel.Implicits._

class TableApiHardcodedSourceDebugTest extends AnyFunSuite with FlinkSpec with Matchers with Inside {

  val sinkId       = "sinkId"
  val sourceId     = "sourceId"
  val resultNodeId = "resultVar"

  private def initializeListener = ResultsCollectingListenerHolder.registerRun

  test("should produce results for each element in list") {
    val listener = initializeListener
    val model = LocalModelData(
      ConfigFactory.empty(),
      FlinkBaseComponentProvider.Components ::: TableApiComponentProvider.ConfigIndependentComponents,
      configCreator = new ConfigCreatorWithCollectingListener(listener),
    )

    val scenario = ScenarioBuilder
      .streaming("test")
      .source(sourceId, "BoundedSource-TableApi")
      .buildSimpleVariable(resultNodeId, "varName", "#input")
      .emptySink(sinkId, "dead-end")

    val results = collectTestResults(model, scenario, listener)
      .nodeResults(resultNodeId)
      .map(c => c.get[Map[String, Any]]("input"))

    results shouldBe List(
      Some(java.util.Map.of("someString", "ABC", "someInt", 1)),
      Some(java.util.Map.of("someString", "DEF", "someInt", 2))
    )
  }

  private def runProcess(model: LocalModelData, testProcess: CanonicalProcess): Unit = {
    val stoppableEnv = flinkMiniCluster.createExecutionEnvironment()
    UnitTestsFlinkRunner.registerInEnvironmentWithModel(stoppableEnv, model)(testProcess)
    stoppableEnv.executeAndWaitForFinished(testProcess.name.value)()
  }

  private def collectTestResults[T](
      model: LocalModelData,
      testProcess: CanonicalProcess,
      collectingListener: ResultsCollectingListener
  ): TestProcess.TestResults = {
    runProcess(model, testProcess)
    collectingListener.results
  }

}
