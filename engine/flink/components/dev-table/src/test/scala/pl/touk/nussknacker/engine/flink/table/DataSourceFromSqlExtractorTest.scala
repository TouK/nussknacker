package pl.touk.nussknacker.engine.flink.table

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class DataSourceFromSqlExtractorTest extends AnyFunSuite with Matchers {

  test("read sql statements from resource") {
    val resourceName = "tables-definition-test1.sql"
    val statement1FromFile =
      """|CREATE TABLE TestTable1
         |(
         |    someString  STRING,
         |    someVarChar VARCHAR(150),
         |    someInt     INT
         |) WITH (
         |      'connector' = 'datagen'
         |)""".stripMargin
    val statement2FromFile =
      s"""|CREATE TABLE TestTable2
          |(
          |    someString2  STRING,
          |    someVarChar2 VARCHAR(150),
          |    someInt2     INT
          |) WITH (
          |      'connector' = 'datagen'
          |)""".stripMargin
    val statements  = SqlFromResourceReader.readFileFromResources(resourceName)
    val sqlsConfigs = DataSourceFromSqlExtractor.extractTablesFromFlinkRuntime(statements)
    // TODO: assert
    1 shouldBe 1
    statements shouldBe 1
  }

}
