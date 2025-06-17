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
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.{ScenarioTestJsonRecord, TestData, TestRecord, TestRecordParser}
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
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.test.EitherValuesDetailedMessage
import pl.touk.nussknacker.test.utils.domain.TestFactory
import pl.touk.nussknacker.ui.api.TestDataSettings
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.PerformTestError.MissingSourceError
import pl.touk.nussknacker.ui.process.test.ScenarioTestService.TestingCapabilitiesError.NoSourcesError
import pl.touk.nussknacker.ui.security.api.LoggedUser

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

      new process.Source with SourceTestSupport[String] with TestDataGenerator {

        override def testRecordParser: TestRecordParser[String] = (testRecords: List[TestRecord]) =>
          testRecords.map { testRecord => CirceUtil.decodeJsonUnsafe[String](testRecord.json) }

        override def generateTestData(size: Int): TestData = TestData((for {
          number <- 1 to size
          record = TestRecord(Json.fromString(s"record $number"))
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

  private val scenarioTestService: ScenarioTestService =
    new ScenarioTestService(
      TestDataSettings(
        maxSamplesCount = None,
        testDataMaxLength = None,
        resultsMaxBytes = None
      ),
      modelData,
      Resource.pure(EngineScenarioCompilationDependencies.empty),
      TestFactory.processResolver(),
      // These dependencies are needed for test execution which is not tested here
      processCounter = null,
      testExecutorService = null,
    )

  test("should detect capabilities for empty scenario") {
    val scenario     = CanonicalProcess(MetaData("empty", StreamMetaData()), List.empty)
    val capabilities = scenarioTestService.getTestingCapabilities(scenario.toScenarioGraph, processVersionFor(scenario))

    capabilities shouldBe Left(NoSourcesError)
  }

  test("should detect capabilities: can parse and generate test data") {
    val scenario     = createScenarioWithSingleSource()
    val capabilities = scenarioTestService.getTestingCapabilities(scenario.toScenarioGraph, processVersionFor(scenario))

    capabilities shouldBe Right(
      TestingCapabilities(canBeTested = true, canFetchLiveData = true, canTestWithForm = false)
    )
  }

  test("should detect capabilities: can only parse test data") {
    val scenario     = createScenarioWithSingleSource("genericSourceNoGenerate")
    val capabilities = scenarioTestService.getTestingCapabilities(scenario.toScenarioGraph, processVersionFor(scenario))

    capabilities shouldBe Right(
      TestingCapabilities(canBeTested = true, canFetchLiveData = false, canTestWithForm = false)
    )
  }

  test("should detect capabilities: does not support testing") {
    val scenario     = createScenarioWithSingleSource("genericSourceNoSupport")
    val capabilities = scenarioTestService.getTestingCapabilities(scenario.toScenarioGraph, processVersionFor(scenario))

    capabilities shouldBe Right(
      TestingCapabilities(canBeTested = false, canFetchLiveData = false, canTestWithForm = false)
    )
  }

  test("should detect capabilities: can create test view") {
    val scenario     = createScenarioWithSingleSource("genericSourceWithTestParameters")
    val capabilities = scenarioTestService.getTestingCapabilities(scenario.toScenarioGraph, processVersionFor(scenario))

    capabilities shouldBe Right(
      TestingCapabilities(canBeTested = true, canFetchLiveData = false, canTestWithForm = true)
    )
  }

  test("should detect capabilities for fragment with valid input") {
    val scenario     = createSimpleFragment()
    val capabilities = scenarioTestService.getTestingCapabilities(scenario.toScenarioGraph, processVersionFor(scenario))
    capabilities shouldBe Right(
      TestingCapabilities(canBeTested = false, canFetchLiveData = false, canTestWithForm = true)
    )
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

    val capabilities = scenarioTestService.getTestingCapabilities(scenario.toScenarioGraph, processVersionFor(scenario))

    capabilities shouldBe Right(
      TestingCapabilities(canBeTested = true, canFetchLiveData = true, canTestWithForm = false)
    )
  }

  test("should detect capabilities for scenario with multiple sources: one can only parse test data") {
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

    val capabilities = scenarioTestService.getTestingCapabilities(scenario.toScenarioGraph, processVersionFor(scenario))

    capabilities shouldBe Right(
      TestingCapabilities(canBeTested = true, canFetchLiveData = false, canTestWithForm = false)
    )
  }

  test("should fetch live data for a scenario with single source") {
    val scenario = createScenarioWithSingleSource()
    val liveData = PreliminaryScenarioRecordsSerDe.noLimit
      .deserialize(
        scenarioTestService
          .fetchSourcesLiveData(scenario.toScenarioGraph, processVersionFor(scenario), isFragment = false, 3)
          .rightValue
      )
      .rightValue

    liveData.records.toList shouldBe List(
      PreliminaryScenarioRecord("source1", Json.fromString("record 1"), timestamp = Some(1)),
      PreliminaryScenarioRecord("source1", Json.fromString("record 2"), timestamp = Some(2)),
      PreliminaryScenarioRecord("source1", Json.fromString("record 3"), timestamp = Some(3)),
    )
  }

  test("should fetch live data for a scenario with single source not providing record timestamps") {
    val scenario = createScenarioWithSingleSource("sourceEmptyTimestamp")
    val liveData =
      PreliminaryScenarioRecordsSerDe.noLimit
        .deserialize(
          scenarioTestService
            .fetchSourcesLiveData(scenario.toScenarioGraph, processVersionFor(scenario), isFragment = false, 3)
            .rightValue
        )
        .rightValue

    liveData.records.toList shouldBe List(
      PreliminaryScenarioRecord("source1", Json.fromString("record 1"), timestamp = None),
      PreliminaryScenarioRecord("source1", Json.fromString("record 2"), timestamp = None),
      PreliminaryScenarioRecord("source1", Json.fromString("record 3"), timestamp = None),
    )
  }

  test("should return error for a source not supporting live data fetching") {
    val scenario = createScenarioWithSingleSource("genericSourceNoGenerate")
    val liveData =
      scenarioTestService.fetchSourcesLiveData(
        scenario.toScenarioGraph,
        processVersionFor(scenario),
        isFragment = false,
        maxNumberOfRecords = 3
      )

    liveData shouldBe Symbol("left")
  }

  test("should return error for empty scenario") {
    val emptyScenario = CanonicalProcess(MetaData("empty", StreamMetaData()), List.empty)

    val liveData =
      scenarioTestService.fetchSourcesLiveData(
        emptyScenario.toScenarioGraph,
        processVersionFor(emptyScenario),
        isFragment = false,
        3
      )

    liveData shouldBe Symbol("left")
  }

  test("should fetch live data for a scenario with multiple sources") {
    val scenario = createScenarioWithMultipleSources()
    val liveData =
      PreliminaryScenarioRecordsSerDe.noLimit
        .deserialize(
          scenarioTestService
            .fetchSourcesLiveData(scenario.toScenarioGraph, processVersionFor(scenario), isFragment = false, 8)
            .rightValue
        )
        .rightValue

    liveData.records.toList shouldBe List(
      PreliminaryScenarioRecord("source1", Json.fromString("record 1"), timestamp = Some(1)),
      PreliminaryScenarioRecord("source3", Json.fromString("record 1"), timestamp = Some(1)),
      PreliminaryScenarioRecord("source1", Json.fromString("record 2"), timestamp = Some(2)),
      PreliminaryScenarioRecord("source3", Json.fromString("record 2"), timestamp = Some(2)),
      PreliminaryScenarioRecord("source1", Json.fromString("record 3"), timestamp = Some(3)),
      PreliminaryScenarioRecord("source2", Json.fromString("record 1"), timestamp = None),
      PreliminaryScenarioRecord("source2", Json.fromString("record 2"), timestamp = None),
      PreliminaryScenarioRecord("source2", Json.fromString("record 3"), timestamp = None),
    )
  }

  test("should fetch requested number of records") {
    val testCases = Table(
      ("scenario", "size", "expected size", "expected size by source id"),
      (createScenarioWithSingleSource(), 0, None, Map.empty),
      (createScenarioWithMultipleSources(), 0, None, Map.empty),
      (createScenarioWithSingleSource(), 1, Some(1), Map("source1" -> 1)),
      (createScenarioWithMultipleSources(), 1, Some(1), Map("source1" -> 1)),
      (createScenarioWithMultipleSources(), 2, Some(2), Map("source1" -> 1, "source2" -> 1)),
      (createScenarioWithMultipleSources(), 3, Some(3), Map("source1" -> 1, "source2" -> 1, "source3" -> 1)),
      (createScenarioWithMultipleSources(), 4, Some(4), Map("source1" -> 2, "source2" -> 1, "source3" -> 1)),
      (createScenarioWithMultipleSources(), 5, Some(5), Map("source1" -> 2, "source2" -> 2, "source3" -> 1)),
      (createScenarioWithMultipleSources(), 6, Some(6), Map("source1" -> 2, "source2" -> 2, "source3" -> 2)),
    )

    forEvery(testCases) { (scenario, size, expectedSize, expectedSizeBySourceId) =>
      val liveData =
        scenarioTestService
          .fetchSourcesLiveData(
            scenario.toScenarioGraph,
            processVersionFor(scenario),
            isFragment = false,
            size
          )
          .map(PreliminaryScenarioRecordsSerDe.noLimit.deserialize(_).rightValue)

      liveData.map(_.records.size).toOption shouldBe expectedSize
      if (expectedSizeBySourceId.nonEmpty) {
        val testRecords = liveData.rightValue.records
        testRecords.toList.groupBy(_.sourceId).mapValuesNow(_.size) shouldBe expectedSizeBySourceId
      }
    }
  }

  test("should prepare scenario test data from records") {
    val scenarioRecords = PreliminaryScenarioRecords(
      NonEmptyList(
        PreliminaryScenarioRecord(sourceId = "source1", record = Json.fromString("record 1"), timestamp = Some(1)),
        PreliminaryScenarioRecord(
          sourceId = "source2",
          record = Json.fromString("record 2"),
          timestamp = None
        ) :: Nil,
      )
    )

    val scenarioTestData =
      scenarioTestService.prepareTestData(scenarioRecords, createScenarioWithMultipleSources()).rightValue

    scenarioTestData.inputRecords shouldBe List(
      ScenarioTestJsonRecord("source1", Json.fromString("record 1"), timestamp = Some(1)),
      ScenarioTestJsonRecord("source2", Json.fromString("record 2")),
    )
  }

  test("should reject record assigned to non-existing source") {
    val scenarioRecords = PreliminaryScenarioRecords(
      NonEmptyList(
        PreliminaryScenarioRecord(sourceId = "source1", record = Json.fromString("record 1"), timestamp = None),
        PreliminaryScenarioRecord(
          sourceId = "non-existing source",
          record = Json.fromString("record 2"),
          timestamp = None
        ) ::
          PreliminaryScenarioRecord(
            sourceId = "non-existing source 2",
            record = Json.fromString("record 3"),
            timestamp = None
          ) :: Nil
      )
    )
    val testCases = Table(
      "scenario",
      createScenarioWithSingleSource(),
      createScenarioWithMultipleSources(),
    )

    forEvery(testCases) { scenario =>
      val error = scenarioTestService.prepareTestData(scenarioRecords, scenario).leftValue

      error shouldBe MissingSourceError(NodeId("non-existing source"), 1)
    }
  }

  test("should get test parameters with defaults") {
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

    val result = scenarioTestService
      .validateAndGetTestParametersDefinition(
        scenarioWithMultipleParams.toScenarioGraph,
        processVersion,
        isFragment = false
      )
      .rightValue

    result.getOrElse(NodeId("source1"), Nil).find(_.name.value == "par1").value.defaultValue shouldBe Some(
      "".spelTemplate
    )
    result.getOrElse(NodeId("source1"), Nil).find(_.name.value == "a").value.defaultValue shouldBe Some("0".spel)
  }

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
