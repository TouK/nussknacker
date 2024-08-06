package pl.touk.nussknacker.engine.flink.table.source

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.flink.table.TableComponentProviderConfig.TestDataGenerationMode
import pl.touk.nussknacker.engine.flink.table.TableTestCases.SimpleTable
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader

class TableSourceDataGenerationTest extends AnyFunSuite with Matchers {

  private val tableSource = new TableSource(
    tableDefinition = SimpleTable.tableDefinition,
    sqlStatements = SqlStatementReader.readSql(SimpleTable.sqlStatement),
    enableFlinkBatchExecutionMode = true,
    testDataGenerationMode = TestDataGenerationMode.Random
  )

  /*
  Note: Testing features like data generation or scenario testing (like ad hoc test) requires a full e2e test where
        Designer is deployed separately (like in a docker container). This is because these features rely on Flink
        Minicluster which relies on proper classloader setup, which is hard to do in simple tests. These tests below
        are useful for checking the output of these methods, but if they pass it doesn't mean that it works e2e.
   */
  test("table source should generate random records with given schema") {
    val records = tableSource.generateTestData(1)

    records.testRecords.size shouldBe 1
    val mapRecord = records.testRecords.head.json.asObject.get.toMap

    mapRecord("someString").isString shouldBe true
    mapRecord("someVarChar").isString shouldBe true
    mapRecord("someInt").isNumber shouldBe true
  }

  test("table source should parse json records") {
    val testData = tableSource.generateTestData(1).testRecords
    val result   = tableSource.testRecordParser.parse(testData).head

    result.getField("someString") shouldBe a[String]
    result.getField("someVarChar") shouldBe a[String]
    result.getField("someInt") shouldBe a[Number]
  }

}
