package pl.touk.nussknacker.devmodel

import com.typesafe.config.ConfigFactory
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.table.TableComponentProvider
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.transformer.FlinkBaseComponentProvider
import pl.touk.nussknacker.engine.process.helpers.ConfigCreatorWithCollectingListener
import pl.touk.nussknacker.engine.process.runner.UnitTestsFlinkRunner
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, ResultsCollectingListenerHolder, TestProcess}

import scala.jdk.CollectionConverters._

class TableApiHardcodedSourceTest extends AnyFunSuite with FlinkSpec with Matchers with Inside {

  val sinkId       = "sinkId"
  val sourceId     = "sourceId"
  val resultNodeId = "resultVar"
  val filterId     = "filter"

  private def initializeListener = ResultsCollectingListenerHolder.registerRun

  test("should produce results for each element in list") {
    val listener = initializeListener
    val model = LocalModelData(
      ConfigFactory.empty(),
      FlinkBaseComponentProvider.Components ::: TableComponentProvider.ConfigIndependentComponents,
      configCreator = new ConfigCreatorWithCollectingListener(listener),
    )

    val scenario = ScenarioBuilder
      .streaming("test")
      .source(sourceId, "hardcodedSource-tableApi")
      .filter(filterId, "#input.someInt != 1")
      .buildSimpleVariable(resultNodeId, "varName", "#input")
      .emptySink(sinkId, "dead-end")

    val results = collectTestResults(model, scenario, listener)
      .nodeResults(resultNodeId)
      .map(c => c.get[java.util.Map[String, Any]]("input"))

    inside(results) { case Some(filteredMap) :: Nil =>
      filteredMap.asScala should contain allOf ("someString" -> "BBB", "someInt" -> 2)
    }
  }

  private def runProcess(model: LocalModelData, testProcess: CanonicalProcess): Unit = {
    val stoppableEnv = flinkMiniCluster.createExecutionEnvironment()
    UnitTestsFlinkRunner.registerInEnvironmentWithModel(stoppableEnv, model)(testProcess)
    stoppableEnv.executeAndWaitForFinished(testProcess.name.value)()
  }

  private def collectTestResults(
      model: LocalModelData,
      testProcess: CanonicalProcess,
      collectingListener: ResultsCollectingListener
  ): TestProcess.TestResults = {
    runProcess(model, testProcess)
    collectingListener.results
  }

}
