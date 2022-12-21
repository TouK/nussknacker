package pl.touk.nussknacker.engine.definition

import com.typesafe.config.ConfigFactory
import io.circe.Json
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.context.transformation.NodeDependencyValue
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.{ScenarioTestRecord, TestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.{CirceUtil, MetaData, StreamMetaData, process}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.validationHelpers.{GenericParametersSource, GenericParametersSourceNoGenerate, GenericParametersSourceNoTestSupport}
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData

class ModelDataTestInfoProviderSpec extends AnyFunSuite with Matchers with OptionValues {

  private val modelData = LocalModelData(ConfigFactory.empty(), new EmptyProcessConfigCreator {
    override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = {
      Map(
        "genericSource" -> WithCategories(new GenericParametersSource),
        "genericSourceNoSupport" -> WithCategories(new GenericParametersSourceNoTestSupport),
        "genericSourceNoGenerate" -> WithCategories(new GenericParametersSourceNoGenerate),
        "sourceEmptyTimestamp" -> WithCategories(SourceGeneratingEmptyTimestamp),
      )
    }
  })

  object SourceGeneratingEmptyTimestamp extends GenericParametersSource {

    override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[List[String]]): process.Source = {

      new process.Source with SourceTestSupport[String] with TestDataGenerator {

        override def testRecordParser: TestRecordParser[String] = (testRecord: TestRecord) => CirceUtil.decodeJsonUnsafe[String](testRecord.json)

        override def generateTestData(size: Int): TestData = TestData((for {
          number <- 1 to size
          record = TestRecord(Json.fromString(s"record $number"))
        } yield record).toList)
      }
    }

  }

  private val metaData: MetaData = MetaData("id", StreamMetaData())

  private val testInfoProvider: TestInfoProvider = new ModelDataTestInfoProvider(modelData)

  test("should detect capabilities for generic transformation source: with support and generate test data") {

    val capabilities = testInfoProvider.getTestingCapabilities(metaData, createScenarioWithSingleSource())

    capabilities shouldBe TestingCapabilities(canBeTested = true, canGenerateTestData = true)
  }

  test("should detect capabilities for generic transformation source: with support, no generate test data") {

    val capabilities = testInfoProvider.getTestingCapabilities(metaData, createScenarioWithSingleSource("genericSourceNoGenerate"))

    capabilities shouldBe TestingCapabilities(canBeTested = true, canGenerateTestData = false)
  }

  test("should detect capabilities for generic transformation source: no support, no generate test data") {

    val capabilities = testInfoProvider.getTestingCapabilities(metaData, createScenarioWithSingleSource("genericSourceNoSupport"))

    capabilities shouldBe TestingCapabilities(canBeTested = false, canGenerateTestData = false)
  }

  test("should generate data for a scenario with single source") {
    val scenarioTestData = testInfoProvider.generateTestData(metaData, createScenarioWithSingleSource(), 3).value

    scenarioTestData.testRecords shouldBe List(
      ScenarioTestRecord("source1", Json.fromString("record 1"), timestamp = Some(1)),
      ScenarioTestRecord("source1", Json.fromString("record 2"), timestamp = Some(2)),
      ScenarioTestRecord("source1", Json.fromString("record 3"), timestamp = Some(3)),
    )
  }

  test("should generate data for a scenario with single source not providing record timestamps") {
    val scenarioTestData = testInfoProvider.generateTestData(metaData, createScenarioWithSingleSource("sourceEmptyTimestamp"), 3).value

    scenarioTestData.testRecords shouldBe List(
      ScenarioTestRecord("source1", Json.fromString("record 1"), timestamp = None),
      ScenarioTestRecord("source1", Json.fromString("record 2"), timestamp = None),
      ScenarioTestRecord("source1", Json.fromString("record 3"), timestamp = None),
    )
  }

  // TODO multiple-sources-test: add multiple sources test
  // TODO multiple-sources-test: test sorting by timestamp

  private def createScenarioWithSingleSource(sourceComponentId: String = "genericSource"): CanonicalProcess = {
    ScenarioBuilder
      .streaming("single source scenario")
      .source("source1", sourceComponentId, "par1" -> "'a'", "a" -> "42")
      .emptySink("end", "dead-end")
  }
}
