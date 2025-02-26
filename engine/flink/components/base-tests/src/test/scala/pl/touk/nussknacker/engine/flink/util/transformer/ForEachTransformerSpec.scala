package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.typeinfo.TypeInfo
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.flink.api.typeinfo.caseclass.CaseClassTypeInfoFactory
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.test.ScalatestMiniClusterJobStatusCheckingOps.miniClusterWithServicesToOps
import pl.touk.nussknacker.engine.flink.util.source.EmitWatermarkAfterEachElementCollectionSource
import pl.touk.nussknacker.engine.process.helpers.ConfigCreatorWithCollectingListener
import pl.touk.nussknacker.engine.process.runner.FlinkScenarioUnitTestJob
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode._

import java.time.Duration

class ForEachTransformerSpec extends AnyFunSuite with FlinkSpec with Matchers with Inside {

  private val sinkId                    = "end"
  private val resultVariableName        = "resultVar"
  private val forEachOutputVariableName = "forEachVar"
  private val forEachNodeResultId       = "for-each-result"

  test("should produce results for each element in list") {
    ResultsCollectingListenerHolder.withListener { collectingListener =>
      val model = modelData(List(TestRecord()), collectingListener)

      val testScenario =
        aProcessWithForEachNode(elements = "{'one', 'other'}", resultExpression = s"#$forEachOutputVariableName + '_1'")

      val results = collectTestResults(model, testScenario, collectingListener)
      extractResultValues(results) shouldBe List("one_1", "other_1")
    }
  }

  test("should produce unique contextId for each element in list") {
    ResultsCollectingListenerHolder.withListener { collectingListener =>
      val model = modelData(List(TestRecord()), collectingListener)

      val testScenario =
        aProcessWithForEachNode(elements = "{'one', 'other'}", resultExpression = s"#$forEachOutputVariableName + '_1'")

      val results = collectTestResults(model, testScenario, collectingListener)
      extractContextIds(results) shouldBe List("forEachProcess-start-0-0-0", "forEachProcess-start-0-0-1")
    }
  }

  test("should set return type based on element types") {
    ResultsCollectingListenerHolder.withListener { collectingListener =>
      val model = modelData(List(TestRecord()), collectingListener)

      val testScenario =
        aProcessWithForEachNode(elements = "{'one', 'other'}", resultExpression = s"#$forEachOutputVariableName + '_1'")
      val processValidator = ProcessValidator.default(model)
      implicit val jobData: JobData =
        JobData(testScenario.metaData, ProcessVersion.empty.copy(processName = testScenario.metaData.name))

      val forEachResultValidationContext =
        processValidator.validate(testScenario, isFragment = false).typing(forEachNodeResultId)
      forEachResultValidationContext.inputValidationContext.get(forEachOutputVariableName) shouldBe Some(Typed[String])
    }
  }

  test("should not produce any results when elements list is empty") {
    ResultsCollectingListenerHolder.withListener { collectingListener =>
      val model = modelData(List(TestRecord()), collectingListener)

      val testScenario = aProcessWithForEachNode(elements = "{}")

      val results = collectTestResults(model, testScenario, collectingListener)
      results.nodeResults shouldNot contain key sinkId
    }
  }

  private def modelData(
      list: List[TestRecord] = List(),
      collectingListener: ResultsCollectingListener[Any]
  ): LocalModelData = {
    val sourceComponent = ComponentDefinition(
      "start",
      SourceFactory.noParamUnboundedStreamFactory[TestRecord](
        EmitWatermarkAfterEachElementCollectionSource.create[TestRecord](list, _.timestamp, Duration.ofHours(1))
      )
    )
    LocalModelData(
      ConfigFactory.empty(),
      sourceComponent :: FlinkBaseComponentProvider.Components,
      configCreator = new ConfigCreatorWithCollectingListener(collectingListener),
    )
  }

  private def aProcessWithForEachNode(elements: String, resultExpression: String = s"#$forEachOutputVariableName") =
    ScenarioBuilder
      .streaming("forEachProcess")
      .parallelism(1)
      .stateOnDisk(true)
      .source("start", "start")
      .customNode("for-each", forEachOutputVariableName, "for-each", "Elements" -> elements.spel)
      .buildSimpleVariable(forEachNodeResultId, "resultVar", resultExpression.spel)
      .emptySink(sinkId, "dead-end")

  private def collectTestResults[T](
      model: LocalModelData,
      testScenario: CanonicalProcess,
      collectingListener: ResultsCollectingListener[T]
  ): TestProcess.TestResults[T] = {
    runScenario(model, testScenario)
    collectingListener.results
  }

  private def extractResultValues(results: TestProcess.TestResults[_]): List[String] = results
    .nodeResults(sinkId)
    .map(_.variableTyped(resultVariableName).get.asInstanceOf[String])

  private def extractContextIds(results: TestProcess.TestResults[_]): List[String] = results
    .nodeResults(forEachNodeResultId)
    .map(_.id)

  private def runScenario(model: LocalModelData, testScenario: CanonicalProcess): Unit = {
    flinkMiniCluster.withDetachedStreamExecutionEnvironment { env =>
      val executionResult = new FlinkScenarioUnitTestJob(model).run(testScenario, env)
      flinkMiniCluster.waitForJobIsFinished(executionResult.getJobID)
    }
  }

}

object TestRecord {
  class TypeInfoFactory extends CaseClassTypeInfoFactory[TestRecord]
}

@TypeInfo(classOf[TestRecord.TypeInfoFactory])
case class TestRecord(id: String = "1", timeHours: Int = 0, eId: Int = 1, str: String = "a") {
  def timestamp: Long = timeHours * 3600L * 1000
}
