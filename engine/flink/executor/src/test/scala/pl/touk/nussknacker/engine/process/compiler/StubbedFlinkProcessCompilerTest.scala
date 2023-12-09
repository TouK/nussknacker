package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory._
import io.circe.Json
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.{CirceUtil, NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.api.process.{
  ProcessObjectDependencies,
  SourceFactory,
  TestWithParametersSupport,
  WithCategories
}
import pl.touk.nussknacker.engine.api.test.{
  ScenarioTestData,
  ScenarioTestJsonRecord,
  ScenarioTestParametersRecord,
  TestRecord,
  TestRecordParser
}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compiledgraph.part.SourcePart
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceTestSupport
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.util.source.{CollectionSource, EmptySource}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.process.helpers.BaseSampleConfigCreator
import pl.touk.nussknacker.engine.resultcollector.PreventInvocationCollector
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.ResultsCollectingListenerHolder
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming

class StubbedFlinkProcessCompilerTest extends AnyFunSuite with Matchers {

  private implicit val intTypeInformation: TypeInformation[Int] = TypeInformation.of(classOf[Int])

  private val scenarioWithSingleSource = ScenarioBuilder
    .streaming("test")
    .source("left-source", "test-source")
    .processorEnd("left-end", "mockService", "all" -> "{}")

  private val scenarioWithSingleTestParametersSource = ScenarioBuilder
    .streaming("test")
    .source("left-source", "test-source-with-parameters-test")
    .processorEnd("left-end", "mockService", "all" -> "{}")

  private val scenarioWithMultipleSources = ScenarioBuilder
    .streaming("test")
    .sources(
      GraphBuilder
        .source("left-source", "test-source")
        .processorEnd("left-end", "mockService", "all" -> "{}"),
      GraphBuilder
        .source("right-source", "test-source2")
        .processorEnd("right-end", "mockService", "all" -> "{}"),
      GraphBuilder
        .source("source-no-test-support", "source-no-test-support")
        .processorEnd("no-test-support-end", "mockService", "all" -> "{}")
    )

  private val minimalFlinkConfig = ConfigFactory.empty
    .withValue("timeout", fromAnyRef("10 seconds"))
    .withValue("asyncExecutionConfig.bufferSize", fromAnyRef(200))
    .withValue("asyncExecutionConfig.workers", fromAnyRef(8))
    .withValue("exceptionHandler.type", fromAnyRef("BrieflyLogging"))
    .withValue("exceptionHandler.withRateMeter", fromAnyRef(true))

  test("stubbing for verification purpose should stub all sources") {
    val modelData = LocalModelData(minimalFlinkConfig, SampleConfigCreator, List.empty)
    val verificationCompiler = VerificationFlinkProcessCompiler(
      scenarioWithMultipleSources,
      modelData
    )
    val compiledProcess = verificationCompiler
      .compileProcess(scenarioWithMultipleSources, ProcessVersion.empty, PreventInvocationCollector)(
        UsedNodes.empty,
        getClass.getClassLoader
      )
      .compileProcessOrFail()
    val sources = compiledProcess.sources.collect { case source: SourcePart =>
      source.obj
    }
    sources should matchPattern { case (_: EmptySource[_]) :: (_: EmptySource[_]) :: (_: EmptySource[_]) :: Nil =>
    }
  }

  test("stubbing for test purpose should work for one source") {
    val scenarioTestData =
      ScenarioTestData(List(1, 2, 3).map(v => ScenarioTestJsonRecord("left-source", Json.fromLong(v))))
    val compiledProcess = testCompile(scenarioWithSingleSource, scenarioTestData)
    val sources = compiledProcess.sources.collect { case source: SourcePart =>
      source.obj
    }
    sources should matchPattern { case CollectionSource(List(1, 2, 3), _, _) :: Nil =>
    }
  }

  test("stubbing for test purpose should work for multiple sources") {
    val scenarioTestData = ScenarioTestData(
      List(
        ScenarioTestJsonRecord("left-source", Json.fromLong(11)),
        ScenarioTestJsonRecord("right-source", Json.fromLong(21)),
        ScenarioTestJsonRecord("right-source", Json.fromLong(22)),
        ScenarioTestJsonRecord("left-source", Json.fromLong(12)),
        ScenarioTestJsonRecord("left-source", Json.fromLong(13)),
        ScenarioTestJsonRecord("right-source", Json.fromLong(23)),
      )
    )

    val compiledProcess = testCompile(scenarioWithMultipleSources, scenarioTestData)

    val sources = compiledProcess.sources.collect { case source: SourcePart =>
      source.node.id -> source.obj
    }.toMap
    sources("left-source") should matchPattern { case CollectionSource(List(11, 12, 13), _, _) =>
    }
    sources("right-source") should matchPattern { case CollectionSource(List(21, 22, 23), _, _) =>
    }
    sources("source-no-test-support") should matchPattern { case EmptySource(_) =>
    }
  }

  test("stubbing for test purpose should work for one source using parameter record") {
    val scenarioTestData = ScenarioTestData(
      List(1, 2, 3).map(v =>
        ScenarioTestParametersRecord(NodeId("left-source"), Map("input" -> Expression("spel", v.toString)))
      )
    )
    val compiledProcess = testCompile(scenarioWithSingleTestParametersSource, scenarioTestData)
    val sources = compiledProcess.sources.collect { case source: SourcePart =>
      source.obj
    }
    sources should matchPattern { case CollectionSource(List(1, 2, 3), _, _) :: Nil =>
    }
  }

  private val modelData =
    LocalModelData(minimalFlinkConfig, SampleConfigCreator, List.empty, objectNaming = DefaultNamespacedObjectNaming)

  private def testCompile(scenario: CanonicalProcess, scenarioTestData: ScenarioTestData) = {
    val testCompiler = new TestFlinkProcessCompiler(
      modelData.configCreator,
      modelData.extractModelDefinitionFun,
      modelData.modelConfig,
      ResultsCollectingListenerHolder.registerRun(identity),
      scenario,
      modelData.objectNaming,
      scenarioTestData
    )
    testCompiler
      .compileProcess(scenario, ProcessVersion.empty, PreventInvocationCollector)(
        UsedNodes.empty,
        getClass.getClassLoader
      )
      .compileProcessOrFail()
  }

  object SampleConfigCreator extends BaseSampleConfigCreator[Int](List.empty) {

    override def sourceFactories(
        processObjectDependencies: ProcessObjectDependencies
    ): Map[String, WithCategories[SourceFactory]] = {
      super.sourceFactories(processObjectDependencies) ++ Map(
        "test-source"  -> WithCategories.anyCategory(SourceFactory.noParam[Int](SampleTestSupportSource)),
        "test-source2" -> WithCategories.anyCategory(SourceFactory.noParam[Int](SampleTestSupportSource)),
        "test-source-with-parameters-test" -> WithCategories.anyCategory(
          SourceFactory.noParam[Int](SampleTestSupportParametersSource)
        ),
        "source-no-test-support" -> WithCategories.anyCategory(
          SourceFactory.noParam[Int](EmptySource(Typed.fromDetailedType[Int]))
        )
      )
    }

  }

  object SampleTestSupportParametersSource
      extends CollectionSource[Int](List.empty, None, Typed.fromDetailedType[Int])
      with FlinkSourceTestSupport[Int]
      with TestWithParametersSupport[Int] {
    override def timestampAssignerForTest: Option[TimestampWatermarkHandler[Int]] = None

    override def testRecordParser: TestRecordParser[Int] = (testRecord: TestRecord) =>
      CirceUtil.decodeJsonUnsafe[Int](testRecord.json)

    override def testParametersDefinition: List[Parameter] = List(Parameter("input", Typed[Int]))

    override def parametersToTestData(params: Map[String, AnyRef]): Int = params("input").asInstanceOf[Int]
  }

  object SampleTestSupportSource
      extends CollectionSource[Int](List.empty, None, Typed.fromDetailedType[Int])
      with FlinkSourceTestSupport[Int] {
    override def timestampAssignerForTest: Option[TimestampWatermarkHandler[Int]] = None
    override def testRecordParser: TestRecordParser[Int] = (testRecord: TestRecord) =>
      CirceUtil.decodeJsonUnsafe[Int](testRecord.json)
  }

}
