package pl.touk.nussknacker.engine.definition.test

import com.typesafe.config.ConfigFactory
import io.circe.Json
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.transformation.NodeDependencyValue
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.{ScenarioTestJsonRecord, TestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.{CirceUtil, MetaData, Params, StreamMetaData, process}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.validationHelpers.{
  GenericParametersSource,
  GenericParametersSourceNoGenerate,
  GenericParametersSourceNoTestSupport,
  SourceWithTestParameters
}
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.test.EitherValuesDetailedMessage

class ModelDataTestInfoProviderSpec
    extends AnyFunSuite
    with Matchers
    with OptionValues
    with EitherValuesDetailedMessage
    with TableDrivenPropertyChecks {

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

        override def testRecordParser: TestRecordParser[String] = (testRecord: TestRecord) =>
          CirceUtil.decodeJsonUnsafe[String](testRecord.json)

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

        override def testRecordParser: TestRecordParser[String] = (_: TestRecord) => ???

        override def generateTestData(size: Int): TestData = TestData(Nil)
      }
    }

  }

  private val testInfoProvider: TestInfoProvider = new ModelDataTestInfoProvider(modelData)

  test("should detect capabilities for empty scenario") {
    val emptyScenario = CanonicalProcess(MetaData("empty", StreamMetaData()), List.empty)

    val capabilities = testInfoProvider.getTestingCapabilities(emptyScenario)

    capabilities shouldBe TestingCapabilities(canBeTested = false, canGenerateTestData = false, canTestWithForm = false)
  }

  test("should detect capabilities: can parse and generate test data") {
    val capabilities = testInfoProvider.getTestingCapabilities(createScenarioWithSingleSource())

    capabilities shouldBe TestingCapabilities(canBeTested = true, canGenerateTestData = true, canTestWithForm = false)
  }

  test("should detect capabilities: can only parse test data") {
    val capabilities =
      testInfoProvider.getTestingCapabilities(createScenarioWithSingleSource("genericSourceNoGenerate"))

    capabilities shouldBe TestingCapabilities(canBeTested = true, canGenerateTestData = false, canTestWithForm = false)
  }

  test("should detect capabilities: does not support testing") {
    val capabilities = testInfoProvider.getTestingCapabilities(createScenarioWithSingleSource("genericSourceNoSupport"))

    capabilities shouldBe TestingCapabilities(canBeTested = false, canGenerateTestData = false, canTestWithForm = false)
  }

  test("should detect capabilities: can create test view") {
    val capabilities =
      testInfoProvider.getTestingCapabilities(createScenarioWithSingleSource("genericSourceWithTestParameters"))
    capabilities shouldBe TestingCapabilities(canBeTested = true, canGenerateTestData = false, canTestWithForm = true)
  }

  test("should detect capabilities for fragment with valid input") {
    val capabilities = testInfoProvider.getTestingCapabilities(createSimpleFragment())
    capabilities shouldBe TestingCapabilities(canBeTested = false, canGenerateTestData = false, canTestWithForm = true)
  }

  test("should detect capabilities for scenario with multiple sources: at least one supports generating and testing") {
    val scenario = ScenarioBuilder
      .streaming("single source scenario")
      .sources(
        GraphBuilder
          .source("source1", "genericSourceNoSupport", "par1" -> "'a'", "a" -> "42")
          .emptySink("end", "dead-end"),
        GraphBuilder
          .source("source2", "genericSource", "par1" -> "'a'", "a" -> "42")
          .emptySink("end", "dead-end"),
      )

    val capabilities = testInfoProvider.getTestingCapabilities(scenario)

    capabilities shouldBe TestingCapabilities(canBeTested = true, canGenerateTestData = true, canTestWithForm = false)
  }

  test("should detect capabilities for scenario with multiple sources: one can only parse test data") {
    val scenario = ScenarioBuilder
      .streaming("single source scenario")
      .sources(
        GraphBuilder
          .source("source1", "genericSourceNoSupport", "par1" -> "'a'", "a" -> "42")
          .emptySink("end", "dead-end"),
        GraphBuilder
          .source("source2", "genericSourceNoGenerate", "par1" -> "'a'", "a" -> "42")
          .emptySink("end", "dead-end"),
      )

    val capabilities = testInfoProvider.getTestingCapabilities(scenario)

    capabilities shouldBe TestingCapabilities(canBeTested = true, canGenerateTestData = false, canTestWithForm = false)
  }

  test("should generate data for a scenario with single source") {
    val scenarioTestData = testInfoProvider.generateTestData(createScenarioWithSingleSource(), 3).value

    scenarioTestData.testRecords shouldBe List(
      PreliminaryScenarioTestRecord.Standard("source1", Json.fromString("record 1"), timestamp = Some(1)),
      PreliminaryScenarioTestRecord.Standard("source1", Json.fromString("record 2"), timestamp = Some(2)),
      PreliminaryScenarioTestRecord.Standard("source1", Json.fromString("record 3"), timestamp = Some(3)),
    )
  }

  test("should generate data for a scenario with single source not providing record timestamps") {
    val scenarioTestData =
      testInfoProvider.generateTestData(createScenarioWithSingleSource("sourceEmptyTimestamp"), 3).value

    scenarioTestData.testRecords shouldBe List(
      PreliminaryScenarioTestRecord.Standard("source1", Json.fromString("record 1"), timestamp = None),
      PreliminaryScenarioTestRecord.Standard("source1", Json.fromString("record 2"), timestamp = None),
      PreliminaryScenarioTestRecord.Standard("source1", Json.fromString("record 3"), timestamp = None),
    )
  }

  test("should generate empty data for a source not supporting generating") {
    val scenarioTestData =
      testInfoProvider.generateTestData(createScenarioWithSingleSource("genericSourceNoGenerate"), 3)

    scenarioTestData shouldBe Symbol("empty")
  }

  test("should generate empty data for empty scenario") {
    val emptyScenario = CanonicalProcess(MetaData("empty", StreamMetaData()), List.empty)

    val scenarioTestData = testInfoProvider.generateTestData(emptyScenario, 3)

    scenarioTestData shouldBe Symbol("empty")
  }

  test("should generate data for a scenario with multiple source") {
    val scenarioTestData = testInfoProvider.generateTestData(createScenarioWithMultipleSources(), 8).value

    scenarioTestData.testRecords shouldBe List(
      PreliminaryScenarioTestRecord.Standard("source1", Json.fromString("record 1"), timestamp = Some(1)),
      PreliminaryScenarioTestRecord.Standard("source3", Json.fromString("record 1"), timestamp = Some(1)),
      PreliminaryScenarioTestRecord.Standard("source1", Json.fromString("record 2"), timestamp = Some(2)),
      PreliminaryScenarioTestRecord.Standard("source3", Json.fromString("record 2"), timestamp = Some(2)),
      PreliminaryScenarioTestRecord.Standard("source1", Json.fromString("record 3"), timestamp = Some(3)),
      PreliminaryScenarioTestRecord.Standard("source2", Json.fromString("record 1"), timestamp = None),
      PreliminaryScenarioTestRecord.Standard("source2", Json.fromString("record 2"), timestamp = None),
      PreliminaryScenarioTestRecord.Standard("source2", Json.fromString("record 3"), timestamp = None),
    )
  }

  test("should generate requested number of records") {
    val testingData = Table(
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

    forEvery(testingData) { (scenario, size, expectedSize, expectedSizeBySourceId) =>
      val testData = testInfoProvider.generateTestData(scenario, size)

      testData.map(_.testRecords.size) shouldBe expectedSize
      if (expectedSizeBySourceId.nonEmpty) {
        val testRecords = testData.value.testRecords.asInstanceOf[List[PreliminaryScenarioTestRecord.Standard]]
        testRecords.groupBy(_.sourceId).mapValuesNow(_.size) shouldBe expectedSizeBySourceId
      }
    }
  }

  test("should prepare scenario test data from standard test records") {
    val preliminaryTestData = PreliminaryScenarioTestData(
      List(
        PreliminaryScenarioTestRecord
          .Standard(sourceId = "source1", record = Json.fromString("record 1"), timestamp = Some(1)),
        PreliminaryScenarioTestRecord.Standard(sourceId = "source2", record = Json.fromString("record 2")),
      )
    )

    val scenarioTestData =
      testInfoProvider.prepareTestData(preliminaryTestData, createScenarioWithMultipleSources()).rightValue

    scenarioTestData.testRecords shouldBe List(
      ScenarioTestJsonRecord("source1", Json.fromString("record 1"), timestamp = Some(1)),
      ScenarioTestJsonRecord("source2", Json.fromString("record 2")),
    )
  }

  test("should prepare scenario test data from test records lacking source id") {
    val preliminaryTestData = PreliminaryScenarioTestData(
      List(
        PreliminaryScenarioTestRecord.Simplified(Json.fromString("record 1")),
        PreliminaryScenarioTestRecord.Simplified(Json.fromString("record 2")),
      )
    )

    val scenarioTestData =
      testInfoProvider.prepareTestData(preliminaryTestData, createScenarioWithSingleSource()).rightValue

    scenarioTestData.testRecords shouldBe List(
      ScenarioTestJsonRecord("source1", Json.fromString("record 1")),
      ScenarioTestJsonRecord("source1", Json.fromString("record 2")),
    )
  }

  test("should reject record assigned to non-existing source") {
    val preliminaryTestData = PreliminaryScenarioTestData(
      List(
        PreliminaryScenarioTestRecord.Standard(sourceId = "source1", record = Json.fromString("record 1")),
        PreliminaryScenarioTestRecord.Standard(sourceId = "non-existing source", record = Json.fromString("record 2")),
        PreliminaryScenarioTestRecord
          .Standard(sourceId = "non-existing source 2", record = Json.fromString("record 3")),
      )
    )
    val testingData = Table(
      "scenario",
      createScenarioWithSingleSource(),
      createScenarioWithMultipleSources(),
    )

    forEvery(testingData) { scenario =>
      val error = testInfoProvider.prepareTestData(preliminaryTestData, scenario).leftValue

      error shouldBe "Record 2 - scenario does not have source id: 'non-existing source'"
    }
  }

  test("should reject record lacking source id if scenario has multiple sources") {
    val preliminaryTestData = PreliminaryScenarioTestData(
      List(
        PreliminaryScenarioTestRecord.Standard(sourceId = "source1", record = Json.fromString("record 1")),
        PreliminaryScenarioTestRecord.Simplified(record = Json.fromString("record 2")),
        PreliminaryScenarioTestRecord.Simplified(record = Json.fromString("record 3")),
      )
    )

    val error = testInfoProvider.prepareTestData(preliminaryTestData, createScenarioWithMultipleSources()).leftValue

    error shouldBe "Record 2 - scenario has multiple sources but got record without source id"
  }

  private def createScenarioWithSingleSource(sourceComponentId: String = "genericSource"): CanonicalProcess = {
    ScenarioBuilder
      .streaming("single source scenario")
      .source("source1", sourceComponentId, "par1" -> "'a'", "a" -> "42")
      .emptySink("end", "dead-end")
  }

  private def createSimpleFragment(): CanonicalProcess = {
    ScenarioBuilder
      .fragment("fragment1", "in" -> classOf[String])
      .fragmentOutput("fragmentEnd", "output", "out" -> "#in")
  }

  private def createScenarioWithMultipleSources(): CanonicalProcess = {
    ScenarioBuilder
      .streaming("single source scenario")
      .sources(
        GraphBuilder
          .source("source1", "genericSource", "par1" -> "'a'", "a" -> "42")
          .emptySink("end", "dead-end"),
        GraphBuilder
          .source("source2", "sourceEmptyTimestamp", "par1" -> "'a'", "a" -> "42")
          .emptySink("end", "dead-end"),
        GraphBuilder
          .source("source3", "genericSource", "par1" -> "'a'", "a" -> "42")
          .emptySink("end", "dead-end"),
        GraphBuilder
          .source("source4", "genericSourceNoSupport", "par1" -> "'a'", "a" -> "42")
          .emptySink("end", "dead-end"),
        GraphBuilder
          .source("source5", "sourceGeneratingEmptyData", "par1" -> "'a'", "a" -> "42")
          .emptySink("end", "dead-end"),
      )
  }

}
