package pl.touk.nussknacker.engine.flink.table.io.source

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.scalatest.funspec.FixtureAnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, LoneElement, Outcome}
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.table.FlinkSqlTableTestCases.allColumnTypesTable
import pl.touk.nussknacker.engine.flink.table.io.definition.discovery.TableDiscoveryImpl
import pl.touk.nussknacker.engine.flink.table.io.definition.{FlinkDataDefinition, FlinkDdlParser}
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import scala.jdk.CollectionConverters._

class TableSourceDataGenerationTest
    extends FixtureAnyFunSpec
    with Matchers
    with LoneElement
    with ValidatedValuesDetailedMessage
    with LazyLogging
    with BeforeAndAfterAll {

  override type FixtureParam = TableSource

  private val miniClusterWithServices =
    FlinkMiniClusterFactory
      .createUnitTestsMiniClusterWithServices()

  override protected def afterAll(): Unit = {
    super.afterAll()
    miniClusterWithServices.close()
  }

  def withFixture(test: OneArgTest): Outcome = {
    miniClusterWithServices.withDetachedStreamExecutionEnvironment { sEnv =>
      val tableSource = {
        val flinkDataDefinition =
          FlinkDataDefinition.applyUnsafe(FlinkDdlParser.parseUnsafe(allColumnTypesTable), None)
        val env = StreamTableEnvironment.create(sEnv)

        val discovery = new TableDiscoveryImpl(List.empty)
        val tableIds  = discovery.discoverTableIdentifiers(flinkDataDefinition, env).loneElement
        val table     = discovery.discoverTable(env, flinkDataDefinition, tableIds)

        new TableSource(
          tableDefinition = table,
          flinkDataDefinition = flinkDataDefinition,
          testDataGenerationMode = TestDataGenerationMode.Random,
          env,
          // This field is not used during test data generation
          watermarkStrategyOptions = null
        )
      }
      test(tableSource)
    }
  }

  /*
  Note: Testing features like data generation or scenario testing (like ad hoc test) requires a full e2e test where
        Designer is deployed separately (like in a docker container). This is because these features rely on Flink
        Minicluster which relies on proper classloader setup, which is hard to do in simple tests. These tests below
        are useful for checking the output of these methods, but if they pass it doesn't mean that it works e2e.
   */
  it("should generate random records with given schema") { tableSource =>
    val records = tableSource.generateTestData(1)

    val mapRecord = records.testRecords.loneElement.json.asObject.get.toMap

    mapRecord.keys should contain theSameElementsAs Set("someString", "someVarChar", "someInt", "file.name")
    mapRecord("someString").isString shouldBe true
    mapRecord("someVarChar").isString shouldBe true
    mapRecord("someInt").isNumber shouldBe true
    mapRecord("file.name").isString shouldBe true
  }

  it("should parse json records") { tableSource =>
    val testData = tableSource.generateTestData(1).testRecords
    val result   = tableSource.testRecordParser.parse(testData).loneElement

    result.getFieldNames(true).asScala should contain theSameElementsAs Set(
      "someString",
      "someVarChar",
      "someInt",
      "someIntComputed",
      "file.name"
    )
    result.getField("someString") shouldBe a[String]
    result.getField("someVarChar") shouldBe a[String]
    result.getField("someInt") shouldBe a[Number]
    result.getField("file.name") shouldBe a[String]
    result.getField("file.name") should not equal "output.ndjson"
  }

}
