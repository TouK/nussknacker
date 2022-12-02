package pl.touk.nussknacker.engine.process.compiler

import cats.data.ValidatedNel
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory._
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.scalatest.LoneElement._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CannotCreateObjectError
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.test.{MultipleSourcesScenarioTestData, ScenarioTestData, SingleSourceScenarioTestData, TestData, TestDataParser}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compiledgraph.CompiledProcessParts
import pl.touk.nussknacker.engine.compiledgraph.part.SourcePart
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceTestSupport
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.util.source.{CollectionSource, EmptySource}
import pl.touk.nussknacker.engine.process.helpers.BaseSampleConfigCreator
import pl.touk.nussknacker.engine.resultcollector.PreventInvocationCollector
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testmode.ResultsCollectingListenerHolder
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

class StubbedFlinkProcessCompilerTest extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage {

  private implicit val intTypeInformation: TypeInformation[Int] = TypeInformation.of(classOf[Int])

  private val scenarioWithSingleSource = ScenarioBuilder.streaming("test")
    .source("left-source", "test-source")
    .processorEnd("left-end", "mockService", "all" -> "{}")

  private val scenarioWithMultipleSources = ScenarioBuilder.streaming("test").sources(
    GraphBuilder
      .source("left-source", "test-source")
      .processorEnd("left-end", "mockService", "all" -> "{}"),
    GraphBuilder
      .source("right-source", "test-source2")
      .processorEnd("right-end", "mockService", "all" -> "{}"))

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
    val testData = SingleSourceScenarioTestData(TestData(Array(1, 2, 3)), 3)
    val compiledProcess = testCompile(scenarioWithSingleSource, testData).validValue
    val sources = compiledProcess.sources.collect {
      case source: SourcePart => source.obj
    }
    sources should matchPattern {
      case CollectionSource(List(1, 2, 3), _, _) :: Nil =>
    }
  }

  test("stubbing for test purpose should work for multiple sources") {
    val testData = MultipleSourcesScenarioTestData(
      Map(
        "left-source" -> TestData(Array(11, 12, 13)),
        "right-source" -> TestData(Array(21, 22, 23)),
      ),
      samplesLimit = 3
    )

    val compiledProcess = testCompile(scenarioWithMultipleSources, testData).validValue

    val sources = compiledProcess.sources.collect {
      case source: SourcePart => source.node.id -> source.obj
    }.toMap
    sources("left-source") should matchPattern {
      case CollectionSource(List(11, 12, 13), _, _) =>
    }
    sources("right-source") should matchPattern {
      case CollectionSource(List(21, 22, 23), _, _) =>
    }
  }

  test("should fail on missing test data for a source") {
    val testData = MultipleSourcesScenarioTestData(Map("left-source" -> TestData(Array(11, 12, 13))), samplesLimit = 3)

    val compilationErrors = testCompile(scenarioWithMultipleSources, testData).invalidValue

    compilationErrors.toList.loneElement shouldBe CannotCreateObjectError("Missing test data for: right-source", "right-source")
  }

  private def testCompile(scenario: CanonicalProcess, scenarioTestData: ScenarioTestData): ValidatedNel[ProcessCompilationError, CompiledProcessParts] = {
    val testCompiler = new TestFlinkProcessCompiler(SampleConfigCreator, minimalFlinkConfig, ResultsCollectingListenerHolder.registerRun(identity),
      scenario, scenarioTestData, DefaultNamespacedObjectNaming)
    testCompiler.compileProcess(scenario, ProcessVersion.empty, DeploymentData.empty, PreventInvocationCollector)(UsedNodes.empty, getClass.getClassLoader).compileProcess()
  }

  object SampleConfigCreator extends BaseSampleConfigCreator[Int](List.empty) {
    override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = {
      super.sourceFactories(processObjectDependencies) ++ Map(
        "test-source" -> WithCategories(SourceFactory.noParam[Int](SampleTestSupportSource)),
        "test-source2" -> WithCategories(SourceFactory.noParam[Int](SampleTestSupportSource))
      )
    }
  }

  object SampleTestSupportSource extends CollectionSource[Int](List.empty, None, Typed.fromDetailedType[Int]) with FlinkSourceTestSupport[Int] {
    override def timestampAssignerForTest: Option[TimestampWatermarkHandler[Int]] = None
    override def testDataParser: TestDataParser[Int] = (data: TestData) => data.testData.map(_.toInt).toList
  }

}
