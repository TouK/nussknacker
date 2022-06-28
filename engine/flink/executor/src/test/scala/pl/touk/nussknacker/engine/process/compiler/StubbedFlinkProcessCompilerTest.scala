package pl.touk.nussknacker.engine.process.compiler

import cats.data.NonEmptyList
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory._
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.scala._
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.test.{TestData, TestDataParser}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.compiledgraph.part.SourcePart
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceTestSupport
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.util.source.{CollectionSource, EmptySource}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.SourceNode
import pl.touk.nussknacker.engine.process.helpers.BaseSampleConfigCreator
import pl.touk.nussknacker.engine.resultcollector.PreventInvocationCollector
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testmode.ResultsCollectingListenerHolder
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming

import scala.concurrent.duration._

class StubbedFlinkProcessCompilerTest extends FunSuite with Matchers {

  private val scenarioWithSingleSource = ScenarioBuilder.streaming("test")
    .source("left-source", "test-source")
    .processorEnd("left-end", "mockService", "all" -> "{}")

  private val scenarioWithMultipleSources = EspProcess(MetaData("test", StreamMetaData()), NonEmptyList.of[SourceNode](
    GraphBuilder
      .source("left-source", "test-source")
      .processorEnd("left-end", "mockService", "all" -> "{}"),
    GraphBuilder
      .source("right-source", "test-source2")
      .processorEnd("right-end", "mockService", "all" -> "{}")))

  private val minimalFlinkConfig = ConfigFactory.empty
    .withValue("timeout", fromAnyRef("10 seconds"))
    .withValue("asyncExecutionConfig.bufferSize", fromAnyRef(200))
    .withValue("asyncExecutionConfig.workers", fromAnyRef(8))
    .withValue("exceptionHandler.type", fromAnyRef("BrieflyLogging"))
    .withValue("exceptionHandler.withRateMeter", fromAnyRef(true))

  test("stubbing for verification purpose should stub all sources") {
    val verificationCompiler = new VerificationFlinkProcessCompiler(scenarioWithMultipleSources, SampleConfigCreator, minimalFlinkConfig, DefaultNamespacedObjectNaming)
    val compiledProcess = verificationCompiler.compileProcess(scenarioWithMultipleSources, ProcessVersion.empty, DeploymentData.empty, PreventInvocationCollector)(UsedNodes.empty, getClass.getClassLoader).compileProcessOrFail()
    val sources = compiledProcess.sources.collect {
      case source: SourcePart => source.obj
    }
    sources should matchPattern {
      case (_: EmptySource[_]) :: (_: EmptySource[_]) :: Nil =>
    }
  }

  test("stubbing for test purpose should work for one source") {
    val testData = TestData(Array(1, 2, 3), 3)
    val compiledProcess = testCompile(scenarioWithSingleSource, testData)
    val sources = compiledProcess.sources.collect {
      case source: SourcePart => source.obj
    }
    sources should matchPattern {
      case CollectionSource(_, List(1, 2, 3), _, _) :: Nil =>
    }
  }

  test("stubbing for test purpose should fail on multiple sources") {
    val testData = TestData(Array(1, 2, 3), 3)
    an[Exception] shouldBe thrownBy {
      testCompile(scenarioWithMultipleSources, testData)
    }
  }

  private def testCompile(scenario: EspProcess, testData: TestData) = {
    val testCompiler = new TestFlinkProcessCompiler(SampleConfigCreator, minimalFlinkConfig, ResultsCollectingListenerHolder.registerRun(identity),
      scenario, testData, new ExecutionConfig, DefaultNamespacedObjectNaming)
    testCompiler.compileProcess(scenario, ProcessVersion.empty, DeploymentData.empty, PreventInvocationCollector)(UsedNodes.empty, getClass.getClassLoader).compileProcessOrFail()
  }

  object SampleConfigCreator extends BaseSampleConfigCreator[Int](List.empty) {
    override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = {
      super.sourceFactories(processObjectDependencies) ++ Map(
        "test-source" -> WithCategories(SourceFactory.noParam[Int](SampleTestSupportSource)),
        "test-source2" -> WithCategories(SourceFactory.noParam[Int](SampleTestSupportSource))
      )
    }
  }

  object SampleTestSupportSource extends CollectionSource[Int](new ExecutionConfig, List.empty, None, Typed.fromDetailedType[Int]) with FlinkSourceTestSupport[Int] {
    override def timestampAssignerForTest: Option[TimestampWatermarkHandler[Int]] = None
    override def testDataParser: TestDataParser[Int] = (data: TestData) => data.testData.map(_.toInt).toList
  }

}
