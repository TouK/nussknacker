package pl.touk.nussknacker.engine.flink.table.source

import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader
import pl.touk.nussknacker.engine.flink.table.extractor.SqlTestData.SimpleTypesTestCase

class TableSourceDataGenerationTest extends AnyFunSuite with Matchers {

  /*
  Note: Testing features like data generation or scenario testing (like ad hoc test) requires a full e2e test where
        Designer is deployed separately (like in a docker container). This is because these features rely on Flink
        Minicluster which relies on proper classloader setup, which is hard to do in simple tests. These tests below
        are useful for checking the output of these methods, but if they pass it doesn't mean that it works e2e.
   */
  test("table source should generate random records with given schema") {
    val tableSource = new TableSource(
      tableDefinition = SimpleTypesTestCase.tableDefinition,
      sqlStatements = SqlStatementReader.readSql(SimpleTypesTestCase.sqlStatement),
      enableFlinkBatchExecutionMode = true
    )
    val records = tableSource.generateTestData(10)

    records.testRecords.size shouldBe 10
    val mapRecord = records.testRecords.head.json.asObject.get.toMap
    mapRecord("someString").isString shouldBe true
    mapRecord("someVarChar").isString shouldBe true
    mapRecord("someInt").isNumber shouldBe true
  }

}
