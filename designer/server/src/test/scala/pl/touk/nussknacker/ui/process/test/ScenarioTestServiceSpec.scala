package pl.touk.nussknacker.ui.process.test

import cats.data.NonEmptyList
import cats.effect.kernel.Resource
import com.typesafe.config.ConfigFactory
import io.circe.Json
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.transformation.NodeDependencyValue
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.livedata.{DataRecord, DataRecords, LiveDataProvider}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test._
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.validationHelpers.{
  GenericParametersSource,
  GenericParametersSourceNoGenerate,
  GenericParametersSourceNoTestSupport,
  SourceWithTestParameters
}
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.TestProcess
import pl.touk.nussknacker.engine.testmode.TestProcess.{FailedAssertion, ResultContext, SuccessfulAssertion}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.test.EitherValuesDetailedMessage
import pl.touk.nussknacker.test.mock.StubFragmentRepository
import pl.touk.nussknacker.test.utils.domain.TestFactory
import pl.touk.nussknacker.ui.api.{TestDataFormat, TestDataSettings}
import pl.touk.nussknacker.ui.process.deployment.ScenarioTestExecutorService
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.PerformTestError.MissingSourceError
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.TestingCapabilitiesError.NoSourcesError
import pl.touk.nussknacker.ui.process.test.testcase.{Assertion, TestCase}
import pl.touk.nussknacker.ui.process.test.testdataformat.CommonDataFormatHandler.InputVariablesParameterName
import pl.touk.nussknacker.ui.process.test.testdataformat.CommonDataFormatSerDe
import pl.touk.nussknacker.ui.processreport.ProcessCounter
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.time.Instant
import scala.concurrent.{Await, ExecutionContext, Future}

class ScenarioTestServiceSpec
    extends AnyFunSuite
    with Matchers
    with OptionValues
    with EitherValuesDetailedMessage
    with TableDrivenPropertyChecks {

  private implicit val user: LoggedUser = TestFactory.adminUser()

  private val modelData = LocalModelData(
    ConfigFactory.empty(),
    List(
      ComponentDefinition("genericSource", new GenericParametersSource),
      ComponentDefinition("genericSourceNoSupport", new GenericParametersSourceNoTestSupport),
      ComponentDefinition("genericSourceNoGenerate", new GenericParametersSourceNoGenerate),
      ComponentDefinition("genericSourceWithTestParameters", new SourceWithTestParameters),
      ComponentDefinition("sourceEmptyTimestamp", SourceGeneratingEmptyTimestamp),
      ComponentDefinition("sourceGeneratingEmptyData", SourceGeneratingEmptyData),
    )
  )

  object SourceGeneratingEmptyTimestamp extends GenericParametersSource {

    override def implementation(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[List[String]]
    ): process.Source = {

      new Source with SourceTestSupport[ProcessingType] with TestDataGenerator with LiveDataProvider {

        override def testRecordParser: TestRecordParser[String] = (testRecords: List[TestRecord]) =>
          testRecords.map { testRecord => CirceUtil.decodeJsonUnsafe[String](testRecord.json) }

        override def generateTestData(maxNumberOfRecords: Int): TestData = TestData((for {
          number <- 1 to maxNumberOfRecords
          record = TestRecord(Json.fromString(s"record $number"))
        } yield record).toList)

        override def fetchLiveData(maxNumberOfRecords: Int): DataRecords = DataRecords((for {
          number <- 1 to maxNumberOfRecords
          record = DataRecord(Map("input" -> s"record $number"), upstreamTimestamp = None)
        } yield record).toList)

      }
    }

  }

  object SourceGeneratingEmptyData extends GenericParametersSource {

    override def implementation(
        params: Params,
        dependencies: List[NodeDependencyValue],
        finalState: Option[List[String]]
    ): process.Source = {

      new process.Source with SourceTestSupport[String] with TestDataGenerator {

        override def testRecordParser: TestRecordParser[String] = (_: List[TestRecord]) => ???

        override def generateTestData(size: Int): TestData = TestData(Nil)
      }
    }

  }

  private val allFormats = Table("format", TestDataFormat.SourceSpecific, TestDataFormat.CommonFormat)

  private val sourceSpecificFormatTestService = prepareScenarioTestService(TestDataFormat.SourceSpecific)
  private val commonFormatTestService         = prepareScenarioTestService(TestDataFormat.CommonFormat)

  test("should detect capabilities for empty scenario") {
    val scenario = CanonicalProcess(MetaData("empty", StreamMetaData()), List.empty)
    forAll(allFormats) { format =>
      val capabilities =
        prepareScenarioTestService(format).getTestingCapabilities(scenario.toScenarioGraph, processVersionFor(scenario))

      capabilities shouldBe Left(NoSourcesError)
    }
  }

  test("should detect capabilities: can parse and generate test data") {
    val scenario = createScenarioWithSingleSource()
    forAll(allFormats) { format =>
      val capabilities =
        prepareScenarioTestService(format)
          .getTestingCapabilities(scenario.toScenarioGraph, processVersionFor(scenario))
          .rightValue

      capabilities.canBeTested shouldBe true
      capabilities.canFetchLiveData shouldBe true
      format match {
        case TestDataFormat.SourceSpecific =>
          capabilities.canTestWithForm shouldBe false
        case TestDataFormat.CommonFormat =>
          capabilities.canTestWithForm shouldBe true
      }
    }
  }

  test("should detect capabilities for source-specific format: can only parse test data") {
    val scenario = createScenarioWithSingleSource("genericSourceNoGenerate")
    val capabilities =
      sourceSpecificFormatTestService.getTestingCapabilities(scenario.toScenarioGraph, processVersionFor(scenario))

    capabilities shouldBe Right(
      TestingCapabilities(canBeTested = true, canFetchLiveData = false, canTestWithForm = false)
    )
  }

  test("should detect capabilities for source-specific format: does not support testing") {
    val scenario = createScenarioWithSingleSource("genericSourceNoSupport")
    val capabilities =
      sourceSpecificFormatTestService.getTestingCapabilities(scenario.toScenarioGraph, processVersionFor(scenario))

    capabilities shouldBe Right(
      TestingCapabilities(canBeTested = false, canFetchLiveData = false, canTestWithForm = false)
    )
  }

  test(
    "should detect capabilities for common format: every source canBeTested and canTestWithForm but only implementing LiveDataProvider canFetchLiveData"
  ) {
    val scenario = createScenarioWithSingleSource("genericSourceNoSupport")
    val capabilities =
      commonFormatTestService.getTestingCapabilities(scenario.toScenarioGraph, processVersionFor(scenario))

    capabilities shouldBe Right(
      TestingCapabilities(canBeTested = true, canFetchLiveData = false, canTestWithForm = true)
    )
  }

  test("should detect capabilities: can test form") {
    val scenario = createScenarioWithSingleSource("genericSourceWithTestParameters")
    val capabilities =
      sourceSpecificFormatTestService.getTestingCapabilities(scenario.toScenarioGraph, processVersionFor(scenario))

    capabilities shouldBe Right(
      TestingCapabilities(canBeTested = true, canFetchLiveData = false, canTestWithForm = true)
    )
  }

  test("should detect capabilities for fragment with valid input") {
    val scenario = createSimpleFragment()
    forAll(allFormats) { format =>
      val capabilities =
        prepareScenarioTestService(format)
          .getTestingCapabilities(scenario.toScenarioGraph, processVersionFor(scenario))
          .rightValue
      capabilities.canFetchLiveData shouldBe false
      capabilities.canTestWithForm shouldBe true
      format match {
        case TestDataFormat.SourceSpecific =>
          capabilities.canBeTested shouldBe false
        case TestDataFormat.CommonFormat =>
          capabilities.canBeTested shouldBe true
      }
    }
  }

  test("should detect capabilities for scenario with multiple sources: at least one supports generating and testing") {
    val scenario = ScenarioBuilder
      .streaming("single source scenario")
      .sources(
        GraphBuilder
          .source("source1", "genericSourceNoSupport", "par1" -> "'a'".spel, "a" -> "42".spel)
          .emptySink("end", "dead-end"),
        GraphBuilder
          .source("source2", "genericSource", "par1" -> "'a'".spel, "a" -> "42".spel)
          .emptySink("end", "dead-end"),
      )

    forAll(allFormats) { format =>
      val capabilities =
        prepareScenarioTestService(format)
          .getTestingCapabilities(scenario.toScenarioGraph, processVersionFor(scenario))
          .rightValue

      capabilities.canBeTested shouldBe true
      capabilities.canFetchLiveData shouldBe true
    }
  }

  test(
    "should detect capabilities for scenario with multiple sources for source-specific format: one can only parse test data"
  ) {
    val scenario = ScenarioBuilder
      .streaming("single source scenario")
      .sources(
        GraphBuilder
          .source("source1", "genericSourceNoSupport", "par1" -> "'a'".spel, "a" -> "42".spel)
          .emptySink("end", "dead-end"),
        GraphBuilder
          .source("source2", "genericSourceNoGenerate", "par1" -> "'a'".spel, "a" -> "42".spel)
          .emptySink("end", "dead-end"),
      )

    val capabilities =
      sourceSpecificFormatTestService.getTestingCapabilities(scenario.toScenarioGraph, processVersionFor(scenario))

    capabilities shouldBe Right(
      TestingCapabilities(canBeTested = true, canFetchLiveData = false, canTestWithForm = false)
    )
  }

  test("should fetch live data for a scenario with single source") {
    val scenario = createScenarioWithSingleSource()
    forAll(allFormats) { format =>
      val liveDataRecords = TestTestDataFormatSerDeFactory
        .create(format)
        .deserializeRecords(
          prepareScenarioTestService(format)
            .fetchSourcesLiveData(scenario.toScenarioGraph, processVersionFor(scenario), isFragment = false, 3)
            .rightValue
        )
        .rightValue

      format match {
        case TestDataFormat.SourceSpecific =>
          liveDataRecords shouldBe List(
            SourceSpecificFormatPreliminaryScenarioRecord(
              NodeId("source1"),
              Json.fromString("record 1"),
              timestamp = Some(1)
            ),
            SourceSpecificFormatPreliminaryScenarioRecord(
              NodeId("source1"),
              Json.fromString("record 2"),
              timestamp = Some(2)
            ),
            SourceSpecificFormatPreliminaryScenarioRecord(
              NodeId("source1"),
              Json.fromString("record 3"),
              timestamp = Some(3)
            ),
          )
        case TestDataFormat.CommonFormat =>
          liveDataRecords shouldBe List(
            CommonFormatPreliminaryScenarioRecord(
              NodeId("source1"),
              Map("input" -> Json.fromString("record 1")),
              upstreamTimestamp = Some(Instant.ofEpochMilli(1))
            ),
            CommonFormatPreliminaryScenarioRecord(
              NodeId("source1"),
              Map("input" -> Json.fromString("record 2")),
              upstreamTimestamp = Some(Instant.ofEpochMilli(2))
            ),
            CommonFormatPreliminaryScenarioRecord(
              NodeId("source1"),
              Map("input" -> Json.fromString("record 3")),
              upstreamTimestamp = Some(Instant.ofEpochMilli(3))
            ),
          )
      }
    }
  }

  test("should fetch live data for a scenario with single source not providing record timestamps") {
    val scenario = createScenarioWithSingleSource("sourceEmptyTimestamp")
    forAll(allFormats) { format =>
      val liveDataRecords = TestTestDataFormatSerDeFactory
        .create(format)
        .deserializeRecords(
          prepareScenarioTestService(format)
            .fetchSourcesLiveData(scenario.toScenarioGraph, processVersionFor(scenario), isFragment = false, 3)
            .rightValue
        )
        .rightValue

      format match {
        case TestDataFormat.SourceSpecific =>
          liveDataRecords shouldBe List(
            SourceSpecificFormatPreliminaryScenarioRecord(
              NodeId("source1"),
              Json.fromString("record 1"),
              timestamp = None
            ),
            SourceSpecificFormatPreliminaryScenarioRecord(
              NodeId("source1"),
              Json.fromString("record 2"),
              timestamp = None
            ),
            SourceSpecificFormatPreliminaryScenarioRecord(
              NodeId("source1"),
              Json.fromString("record 3"),
              timestamp = None
            ),
          )
        case TestDataFormat.CommonFormat =>
          liveDataRecords shouldBe List(
            CommonFormatPreliminaryScenarioRecord(
              NodeId("source1"),
              Map("input" -> Json.fromString("record 1")),
              upstreamTimestamp = None
            ),
            CommonFormatPreliminaryScenarioRecord(
              NodeId("source1"),
              Map("input" -> Json.fromString("record 2")),
              upstreamTimestamp = None
            ),
            CommonFormatPreliminaryScenarioRecord(
              NodeId("source1"),
              Map("input" -> Json.fromString("record 3")),
              upstreamTimestamp = None
            ),
          )
      }
    }
  }

  test("should return error for a source not supporting live data fetching") {
    val scenario = createScenarioWithSingleSource("genericSourceNoGenerate")
    forAll(allFormats) { format =>
      val liveData =
        prepareScenarioTestService(format).fetchSourcesLiveData(
          scenario.toScenarioGraph,
          processVersionFor(scenario),
          isFragment = false,
          maxNumberOfRecords = 3
        )

      liveData shouldBe Symbol("left")
    }
  }

  test("should return error for empty scenario") {
    val emptyScenario = CanonicalProcess(MetaData("empty", StreamMetaData()), List.empty)

    forAll(allFormats) { format =>
      val liveData =
        prepareScenarioTestService(format).fetchSourcesLiveData(
          emptyScenario.toScenarioGraph,
          processVersionFor(emptyScenario),
          isFragment = false,
          3
        )

      liveData shouldBe Symbol("left")
    }
  }

  test("should fetch live data for a scenario with multiple sources") {
    val scenario = createScenarioWithMultipleSources()
    forAll(allFormats) { format =>
      val liveDataRecords =
        TestTestDataFormatSerDeFactory
          .create(format)
          .deserializeRecords(
            prepareScenarioTestService(format)
              .fetchSourcesLiveData(scenario.toScenarioGraph, processVersionFor(scenario), isFragment = false, 8)
              .rightValue
          )
          .rightValue

      format match {
        case TestDataFormat.SourceSpecific =>
          liveDataRecords shouldBe List(
            SourceSpecificFormatPreliminaryScenarioRecord(
              NodeId("source1"),
              Json.fromString("record 1"),
              timestamp = Some(1)
            ),
            SourceSpecificFormatPreliminaryScenarioRecord(
              NodeId("source3"),
              Json.fromString("record 1"),
              timestamp = Some(1)
            ),
            SourceSpecificFormatPreliminaryScenarioRecord(
              NodeId("source1"),
              Json.fromString("record 2"),
              timestamp = Some(2)
            ),
            SourceSpecificFormatPreliminaryScenarioRecord(
              NodeId("source3"),
              Json.fromString("record 2"),
              timestamp = Some(2)
            ),
            SourceSpecificFormatPreliminaryScenarioRecord(
              NodeId("source1"),
              Json.fromString("record 3"),
              timestamp = Some(3)
            ),
            SourceSpecificFormatPreliminaryScenarioRecord(
              NodeId("source2"),
              Json.fromString("record 1"),
              timestamp = None
            ),
            SourceSpecificFormatPreliminaryScenarioRecord(
              NodeId("source2"),
              Json.fromString("record 2"),
              timestamp = None
            ),
            SourceSpecificFormatPreliminaryScenarioRecord(
              NodeId("source2"),
              Json.fromString("record 3"),
              timestamp = None
            ),
          )
        case TestDataFormat.CommonFormat =>
          liveDataRecords shouldBe List(
            CommonFormatPreliminaryScenarioRecord(
              NodeId("source1"),
              Map("input" -> Json.fromString("record 1")),
              upstreamTimestamp = Some(Instant.ofEpochMilli(1))
            ),
            CommonFormatPreliminaryScenarioRecord(
              NodeId("source3"),
              Map("input" -> Json.fromString("record 1")),
              upstreamTimestamp = Some(Instant.ofEpochMilli(1))
            ),
            CommonFormatPreliminaryScenarioRecord(
              NodeId("source1"),
              Map("input" -> Json.fromString("record 2")),
              upstreamTimestamp = Some(Instant.ofEpochMilli(2))
            ),
            CommonFormatPreliminaryScenarioRecord(
              NodeId("source3"),
              Map("input" -> Json.fromString("record 2")),
              upstreamTimestamp = Some(Instant.ofEpochMilli(2))
            ),
            CommonFormatPreliminaryScenarioRecord(
              NodeId("source1"),
              Map("input" -> Json.fromString("record 3")),
              upstreamTimestamp = Some(Instant.ofEpochMilli(3))
            ),
            CommonFormatPreliminaryScenarioRecord(
              NodeId("source2"),
              Map("input" -> Json.fromString("record 1")),
              upstreamTimestamp = None
            ),
            CommonFormatPreliminaryScenarioRecord(
              NodeId("source2"),
              Map("input" -> Json.fromString("record 2")),
              upstreamTimestamp = None
            ),
            CommonFormatPreliminaryScenarioRecord(
              NodeId("source2"),
              Map("input" -> Json.fromString("record 3")),
              upstreamTimestamp = None
            ),
          )
      }
    }
  }

  test("should fetch requested number of records") {
    val testCases = Table(
      ("scenario", "size", "expected size", "expected size by source id"),
      (createScenarioWithSingleSource(), 0, None, Map.empty),
      (createScenarioWithMultipleSources(), 0, None, Map.empty),
      (createScenarioWithSingleSource(), 1, Some(1), Map(NodeId("source1") -> 1)),
      (createScenarioWithMultipleSources(), 1, Some(1), Map(NodeId("source1") -> 1)),
      (createScenarioWithMultipleSources(), 2, Some(2), Map(NodeId("source1") -> 1, NodeId("source2") -> 1)),
      (
        createScenarioWithMultipleSources(),
        3,
        Some(3),
        Map(NodeId("source1") -> 1, NodeId("source2") -> 1, NodeId("source3") -> 1)
      ),
      (
        createScenarioWithMultipleSources(),
        4,
        Some(4),
        Map(NodeId("source1") -> 2, NodeId("source2") -> 1, NodeId("source3") -> 1)
      ),
      (
        createScenarioWithMultipleSources(),
        5,
        Some(5),
        Map(NodeId("source1") -> 2, NodeId("source2") -> 2, NodeId("source3") -> 1)
      ),
      (
        createScenarioWithMultipleSources(),
        6,
        Some(6),
        Map(NodeId("source1") -> 2, NodeId("source2") -> 2, NodeId("source3") -> 2)
      ),
    )

    forAll(allFormats) { format =>
      val testService = prepareScenarioTestService(format)
      val serde       = TestTestDataFormatSerDeFactory.create(format)
      forEvery(testCases) { (scenario, size, expectedSize, expectedSizeBySourceId) =>
        val liveDataRecords =
          testService
            .fetchSourcesLiveData(
              scenario.toScenarioGraph,
              processVersionFor(scenario),
              isFragment = false,
              size
            )
            .map(serde.deserializeRecords(_).rightValue)

        liveDataRecords.map(_.size).toOption shouldBe expectedSize
        if (expectedSizeBySourceId.nonEmpty) {
          val testRecords = liveDataRecords.rightValue
          testRecords.groupBy(_.sourceId).mapValuesNow(_.size) shouldBe expectedSizeBySourceId
        }
      }
    }
  }

  test("should prepare scenario test data from records for source-specific format") {
    val scenarioRecords = PreliminaryScenarioRecords(
      NonEmptyList(
        SourceSpecificFormatPreliminaryScenarioRecord(
          sourceId = NodeId("source1"),
          record = Json.fromString("record 1"),
          timestamp = Some(1)
        ),
        SourceSpecificFormatPreliminaryScenarioRecord(
          sourceId = NodeId("source2"),
          record = Json.fromString("record 2"),
          timestamp = None
        ) :: Nil,
      )
    )

    val scenarioTestData =
      sourceSpecificFormatTestService.prepareTestData(scenarioRecords, createScenarioWithMultipleSources()).rightValue

    scenarioTestData.inputRecords shouldBe List(
      ScenarioTestSourceSpecificFormatJsonRecord(NodeId("source1"), Json.fromString("record 1"), timestamp = Some(1L)),
      ScenarioTestSourceSpecificFormatJsonRecord(NodeId("source2"), Json.fromString("record 2")),
    )
  }

  test("should prepare scenario test data from records for common format") {
    val scenarioRecords = PreliminaryScenarioRecords(
      NonEmptyList(
        CommonFormatPreliminaryScenarioRecord(
          sourceId = NodeId("source1"),
          variables = Map("input" -> Json.fromString("record 1")),
          upstreamTimestamp = Some(Instant.ofEpochMilli(1))
        ),
        CommonFormatPreliminaryScenarioRecord(
          sourceId = NodeId("source2"),
          variables = Map("input" -> Json.fromString("record 2")),
          upstreamTimestamp = None
        ) :: Nil,
      )
    )

    val scenarioTestData =
      commonFormatTestService.prepareTestData(scenarioRecords, createScenarioWithMultipleSources()).rightValue

    scenarioTestData.inputRecords shouldBe List(
      ScenarioTestCommonFormatJsonRecord(
        NodeId("source1"),
        Map("input" -> Json.fromString("record 1")),
        upstreamTimestamp = Some(Instant.ofEpochMilli(1))
      ),
      ScenarioTestCommonFormatJsonRecord(
        NodeId("source2"),
        Map("input" -> Json.fromString("record 2")),
        upstreamTimestamp = None
      ),
    )
  }

  test("should reject record assigned to non-existing source") {
    forAll(allFormats) { format =>
      val testService = prepareScenarioTestService(format)

      val inputRecords =
        format match {
          case TestDataFormat.SourceSpecific =>
            PreliminaryScenarioRecords(
              NonEmptyList(
                SourceSpecificFormatPreliminaryScenarioRecord(
                  sourceId = NodeId("source1"),
                  record = Json.fromString("record 1"),
                  timestamp = None
                ),
                SourceSpecificFormatPreliminaryScenarioRecord(
                  sourceId = NodeId("non-existing source"),
                  record = Json.fromString("record 2"),
                  timestamp = None
                ) ::
                  SourceSpecificFormatPreliminaryScenarioRecord(
                    sourceId = NodeId("non-existing source 2"),
                    record = Json.fromString("record 3"),
                    timestamp = None
                  ) :: Nil
              )
            )
          case TestDataFormat.CommonFormat =>
            PreliminaryScenarioRecords(
              NonEmptyList(
                CommonFormatPreliminaryScenarioRecord(
                  sourceId = NodeId("source1"),
                  variables = Map("input" -> Json.fromString("record 1")),
                  upstreamTimestamp = None
                ),
                CommonFormatPreliminaryScenarioRecord(
                  sourceId = NodeId("non-existing source"),
                  variables = Map("input" -> Json.fromString("record 2")),
                  upstreamTimestamp = None
                ) ::
                  CommonFormatPreliminaryScenarioRecord(
                    sourceId = NodeId("non-existing source 2"),
                    variables = Map("input" -> Json.fromString("record 3")),
                    upstreamTimestamp = None
                  ) :: Nil
              )
            )
        }

      forEvery(
        Table(
          "scenario",
          createScenarioWithSingleSource(),
          createScenarioWithMultipleSources(),
        )
      ) { scenario =>
        val error = testService.prepareTestData(inputRecords, scenario).leftValue

        error shouldBe MissingSourceError(NodeId("non-existing source"), 1)
      }
    }
  }

  test("should get test parameters with defaults for source-specific format") {
    val scenarioWithMultipleParams = ScenarioBuilder
      .streaming("single source scenario")
      .source(
        "source1",
        "genericSourceWithTestParameters",
        "par1" -> "a".spelTemplate,
        "a"    -> "42".spel
      )
      .emptySink("end", "dead-end")
    val processVersion = processVersionFor(scenarioWithMultipleParams)

    forAll(allFormats) { format =>
      val testService = prepareScenarioTestService(format)

      val result = testService
        .validateAndGetTestParametersDefinition(
          scenarioWithMultipleParams.toScenarioGraph,
          processVersion,
          isFragment = false
        )
        .rightValue

      result.keySet shouldBe Set(NodeId("source1"))
      val parametersForSource1 = result.get(NodeId("source1")).value

      format match {
        case TestDataFormat.SourceSpecific =>
          parametersForSource1.map(_.name.value) shouldBe List("par1", "lazyPar1", "a")
          parametersForSource1.find(_.name.value == "par1").value.defaultValue.value shouldBe "''".spel
          parametersForSource1.find(_.name.value == "a").value.defaultValue.value shouldBe "0".spel
        case TestDataFormat.CommonFormat =>
          parametersForSource1.map(_.name) shouldBe List(InputVariablesParameterName)
          parametersForSource1.find(_.name == InputVariablesParameterName).value.defaultValue.value shouldBe
            """{
              |  "otherNameThanInput" : {
              |    "a" : 42
              |  }
              |}""".stripMargin.jsonExpression
      }
    }
  }

  test("should run test case") {
    import scala.concurrent.ExecutionContext.Implicits._
    import scala.concurrent.duration._

    val now = Instant.now()
    val mockedTestResult = TestProcess.TestResults
      .empty[Json]
      .copy(
        originalNodeResults = Map(
          NodeId("someSource") -> (ResultContext[Any](ContextId.dummy, now, Map("someVariable" -> "foo")) ::
            ResultContext[Any](ContextId.dummy, now, Map("someVariable" -> "bar")) :: Nil)
        )
      )

    val mockedTestExecutorService = new ScenarioTestExecutorService() {
      override def testProcess(
          processVersion: ProcessVersion,
          canonicalProcess: CanonicalProcess,
          scenarioTestData: ScenarioTestData
      )(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[TestProcess.TestResults[Json]] = {
        Future.successful(mockedTestResult)
      }
    }

    val testService = new ScenarioTestService(
      TestDataSettings(
        maxSamplesCount = None,
        testDataMaxLength = None,
        resultsMaxBytes = None,
        TestDataFormat.CommonFormat,
      ),
      modelData,
      Resource.pure(EngineScenarioCompilationDependencies.empty),
      TestFactory.processResolver(),
      processCounter = new ProcessCounter(new StubFragmentRepository(Map.empty)),
      testExecutorService = mockedTestExecutorService,
      TestFactory.processValidator()
    )

    val scenarioRecords = PreliminaryScenarioRecords(
      NonEmptyList(
        CommonFormatPreliminaryScenarioRecord(
          sourceId = NodeId("source1"),
          variables = Map("input" -> Json.fromString("record 1")),
          upstreamTimestamp = Some(Instant.ofEpochMilli(1))
        ),
        Nil,
      )
    )

    val preliminaryRecordsSerDe = new PreliminaryScenarioRecordsSerDe(None, None, CommonDataFormatSerDe)
    val serializedRecords: SerializedScenarioRecordsContent = preliminaryRecordsSerDe
      .serialize(scenarioRecords)
      .getOrElse(throw new RuntimeException("Error during records serialization"))

    val testCase = TestCase(
      "someTest",
      serializedRecords.content,
      Map.empty,
      Map(
        NodeId("someSource") -> List(
          Assertion("#TEST.assertEquals(#contexts[0].someVariable, 'foo')"),
          Assertion("#TEST.assertEquals(#contexts[1].someVariable, 'foo')")
        )
      )
    )

    val scenario = createScenarioWithSingleSource()

    val result = Await
      .result(
        testService.performTestCase(
          scenario.toScenarioGraph,
          processVersionFor(scenario),
          isFragment = false,
          testCase
        ),
        20 seconds
      )
      .getOrElse(throw new RuntimeException("Error during performing test"))

    result.results.assertionsResults(NodeId("someSource")) shouldBe List(
      SuccessfulAssertion,
      FailedAssertion("Expected: foo but found bar")
    )
  }

  private def prepareScenarioTestService(testDataFormat: TestDataFormat.Value): ScenarioTestService =
    new ScenarioTestService(
      TestDataSettings(
        maxSamplesCount = None,
        testDataMaxLength = None,
        resultsMaxBytes = None,
        testDataFormat,
      ),
      modelData,
      Resource.pure(EngineScenarioCompilationDependencies.empty),
      TestFactory.processResolver(),
      // These dependencies are needed for test execution which is not tested here
      processCounter = null,
      testExecutorService = null,
      uiProcessValidator = null
    )

  private def createScenarioWithSingleSource(sourceComponentId: String = "genericSource"): CanonicalProcess = {
    ScenarioBuilder
      .streaming("single source scenario")
      .source("source1", sourceComponentId, "par1" -> "'a'".spel, "a" -> "42".spel)
      .emptySink("end", "dead-end")
  }

  private def createSimpleFragment(): CanonicalProcess = {
    ScenarioBuilder
      .fragment("fragment1", "in" -> classOf[String])
      .fragmentOutput("fragmentEnd", "output", "out" -> "#in".spel)
  }

  private def createScenarioWithMultipleSources(): CanonicalProcess = {
    ScenarioBuilder
      .streaming("single source scenario")
      .sources(
        GraphBuilder
          .source("source1", "genericSource", "par1" -> "'a'".spel, "a" -> "42".spel)
          .emptySink("end", "dead-end"),
        GraphBuilder
          .source("source2", "sourceEmptyTimestamp", "par1" -> "'a'".spel, "a" -> "42".spel)
          .emptySink("end", "dead-end"),
        GraphBuilder
          .source("source3", "genericSource", "par1" -> "'a'".spel, "a" -> "42".spel)
          .emptySink("end", "dead-end"),
        GraphBuilder
          .source("source4", "genericSourceNoSupport", "par1" -> "'a'".spel, "a" -> "42".spel)
          .emptySink("end", "dead-end"),
        GraphBuilder
          .source("source5", "sourceGeneratingEmptyData", "par1" -> "'a'".spel, "a" -> "42".spel)
          .emptySink("end", "dead-end"),
      )
  }

  private def processVersionFor(scenario: CanonicalProcess) = {
    ProcessVersion.empty.copy(processName = scenario.metaData.name)
  }

}
