package pl.touk.nussknacker.engine.process.scenariotesting

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory._
import io.circe.Json
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{CirceUtil, JobData, NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.definition.{EngineScenarioCompilationDependencies, Parameter}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{SourceFactory, TestWithParametersSupport}
import pl.touk.nussknacker.engine.api.test._
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compiledgraph.CompiledProcessParts
import pl.touk.nussknacker.engine.compiledgraph.part.SourcePart
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceTestSupport
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.process.compiler.UsedNodes
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.MockService
import pl.touk.nussknacker.engine.process.helpers.TestResultsHolder
import pl.touk.nussknacker.engine.process.scenariotesting.StubbedFlinkProcessCompilerDataFactoryTest.mockServiceResultsHolder
import pl.touk.nussknacker.engine.resultcollector.PreventInvocationCollector
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.ResultsCollectingListenerHolder

class StubbedFlinkProcessCompilerDataFactoryTest extends AnyFunSuite with Matchers {

  private val scenarioWithSingleSource = ScenarioBuilder
    .streaming("test")
    .source("left-source", "test-source")
    .processorEnd("left-end", "mockService", "all" -> "{}".spel)

  private val scenarioWithSingleTestParametersSource = ScenarioBuilder
    .streaming("test")
    .source("left-source", "test-source-with-parameters-test")
    .processorEnd("left-end", "mockService", "all" -> "{}".spel)

  private val scenarioWithMultipleSources = ScenarioBuilder
    .streaming("test")
    .sources(
      GraphBuilder
        .source("left-source", "test-source")
        .processorEnd("left-end", "mockService", "all" -> "{}".spel),
      GraphBuilder
        .source("right-source", "test-source2")
        .processorEnd("right-end", "mockService", "all" -> "{}".spel),
      GraphBuilder
        .source("source-no-test-support", "source-no-test-support")
        .processorEnd("no-test-support-end", "mockService", "all" -> "{}".spel)
    )

  private val minimalFlinkConfig = ConfigFactory.empty
    .withValue("timeout", fromAnyRef("10 seconds"))
    .withValue("asyncExecutionConfig.bufferSize", fromAnyRef(200))
    .withValue("asyncExecutionConfig.workers", fromAnyRef(8))
    .withValue("exceptionHandler.type", fromAnyRef("BrieflyLogging"))
    .withValue("exceptionHandler.withRateMeter", fromAnyRef(true))

  private val components = List(
    ComponentDefinition("test-source", SourceFactory.noParamUnboundedStreamFactory[Int](SampleTestSupportSource)),
    ComponentDefinition("test-source2", SourceFactory.noParamUnboundedStreamFactory[Int](SampleTestSupportSource)),
    ComponentDefinition(
      "test-source-with-parameters-test",
      SourceFactory.noParamUnboundedStreamFactory[Int](SampleTestSupportParametersSource)
    ),
    ComponentDefinition(
      "source-no-test-support",
      SourceFactory.noParamUnboundedStreamFactory[Int](
        new DataRecordsSource(List.empty, ValidationContext.empty, None)
      )
    ),
    ComponentDefinition("mockService", new MockService(mockServiceResultsHolder))
  )

  private val modelData =
    LocalModelData(
      minimalFlinkConfig,
      components
    )

  test("stubbing for verification purpose should stub all sources") {
    val verificationCompilerFactory = VerificationFlinkProcessCompilerDataFactory(modelData)
    val compiledProcess = verificationCompilerFactory
      .prepareCompilerData(scenarioWithMultipleSources.metaData, ProcessVersion.empty, PreventInvocationCollector)(
        UsedNodes.empty,
        getClass.getClassLoader
      )
      .compileProcessOrFail(scenarioWithMultipleSources)(EngineScenarioCompilationDependencies.empty)
    val sources = compiledProcess.sources.collect { case source: SourcePart =>
      source.obj
    }
    sources should matchPattern {
      case (_: DataRecordsSource) :: (_: DataRecordsSource) :: (_: DataRecordsSource) :: Nil =>
    }
  }

  test("stubbing for test purpose should work for one source") {
    val scenarioTestData =
      ScenarioTestData(
        List(1, 2, 3).map(v => ScenarioTestSourceSpecificFormatJsonRecord(NodeId("left-source"), Json.fromLong(v)))
      )
    val compiledProcess = testCompile(scenarioWithSingleSource, scenarioTestData)
    val sources = compiledProcess.sources.collect { case source: SourcePart =>
      source.obj
    }
    sources should matchPattern { case CollectionSource(List(1, 2, 3), _, _, _) :: Nil =>
    }
  }

  test("stubbing for test purpose should work for multiple sources") {
    val scenarioTestData = ScenarioTestData(
      List(
        ScenarioTestSourceSpecificFormatJsonRecord(NodeId("left-source"), Json.fromLong(11)),
        ScenarioTestSourceSpecificFormatJsonRecord(NodeId("right-source"), Json.fromLong(21)),
        ScenarioTestSourceSpecificFormatJsonRecord(NodeId("right-source"), Json.fromLong(22)),
        ScenarioTestSourceSpecificFormatJsonRecord(NodeId("left-source"), Json.fromLong(12)),
        ScenarioTestSourceSpecificFormatJsonRecord(NodeId("left-source"), Json.fromLong(13)),
        ScenarioTestSourceSpecificFormatJsonRecord(NodeId("right-source"), Json.fromLong(23)),
      )
    )

    val compiledProcess = testCompile(scenarioWithMultipleSources, scenarioTestData)

    val sources = compiledProcess.sources.collect { case source: SourcePart =>
      source.node.id -> source.obj
    }.toMap
    sources(NodeId("left-source")) should matchPattern { case CollectionSource(List(11, 12, 13), _, _, _) =>
    }
    sources(NodeId("right-source")) should matchPattern { case CollectionSource(List(21, 22, 23), _, _, _) =>
    }
    sources(NodeId("source-no-test-support")) should matchPattern { case _: DataRecordsSource =>
    }
  }

  test("stubbing for test purpose should work for one source using parameter record") {
    val scenarioTestData = ScenarioTestData(
      List(1, 2, 3).map(v =>
        ScenarioTestParametersRecord(
          NodeId("left-source"),
          Map(ParameterName("input") -> Expression(Language.Spel, v.toString))
        )
      )
    )
    val compiledProcess = testCompile(scenarioWithSingleTestParametersSource, scenarioTestData)
    val sources = compiledProcess.sources.collect { case source: SourcePart =>
      source.obj
    }
    sources should matchPattern { case CollectionSource(List(1, 2, 3), _, _, _) :: Nil =>
    }
  }

  private def testCompile(scenario: CanonicalProcess, scenarioTestData: ScenarioTestData): CompiledProcessParts = {
    val jobData = JobData(scenario.metaData, ProcessVersion.empty.copy(processName = scenario.metaData.name))

    val testCompilerFactory = TestFlinkProcessCompilerDataFactory(
      scenario,
      scenarioTestData,
      modelData,
      jobData,
      ResultsCollectingListenerHolder.noopListener
    )
    testCompilerFactory
      .prepareCompilerData(jobData.metaData, jobData.processVersion, PreventInvocationCollector)(
        UsedNodes.empty,
        getClass.getClassLoader
      )
      .compileProcessOrFail(scenario)(EngineScenarioCompilationDependencies.empty)
  }

  object SampleTestSupportParametersSource
      extends CollectionSource[Int](List.empty, None, Typed.fromDetailedType[Int])
      with FlinkSourceTestSupport[Int]
      with TestWithParametersSupport[Int] {
    override def watermarkStrategyForTest: Option[WatermarkStrategy[Int]] = None

    override def testRecordParser: TestRecordParser[Int] = (testRecords: List[TestRecord]) =>
      testRecords.map { testRecord =>
        CirceUtil.decodeJsonUnsafe[Int](testRecord.json)
      }

    override def testParametersDefinition: List[Parameter] = List(Parameter(ParameterName("input"), Typed[Int]))

    override def parametersToTestData(params: Map[ParameterName, AnyRef]): Int =
      params(ParameterName("input")).asInstanceOf[Int]
  }

  object SampleTestSupportSource
      extends CollectionSource[Int](List.empty, None, Typed.fromDetailedType[Int])
      with FlinkSourceTestSupport[Int] {
    override def watermarkStrategyForTest: Option[WatermarkStrategy[Int]] = None

    override def testRecordParser: TestRecordParser[Int] = (testRecords: List[TestRecord]) =>
      testRecords.map { testRecord =>
        CirceUtil.decodeJsonUnsafe[Int](testRecord.json)
      }

  }

}

object StubbedFlinkProcessCompilerDataFactoryTest extends Serializable {

  val mockServiceResultsHolder = new TestResultsHolder[Any]

}
