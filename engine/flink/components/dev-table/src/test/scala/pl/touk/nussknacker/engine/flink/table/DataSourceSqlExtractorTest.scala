package pl.touk.nussknacker.engine.flink.table

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.flink.table.SqlTestData.{SimpleTypesTestCase, invalidSqlStatements}
import pl.touk.nussknacker.engine.flink.table.extractor._

class DataSourceSqlExtractorTest extends AnyFunSuite with Matchers with TableDrivenPropertyChecks {

  test("extracts configuration from valid sql statement") {
    val statements        = SqlStatementReader.readSql(SimpleTypesTestCase.sqlStatement)
    val dataSourceConfigs = DataSourceSqlExtractor.extractTablesFromFlinkRuntime(statements)

    val expectedResult = DataSourceSqlExtractorResult(
      tableDefinitions = List(
        DataSourceTableDefinition(
          SimpleTypesTestCase.tableName,
          SimpleTypesTestCase.schemaTypingResult
        )
      ),
      sqlStatementExecutionErrors = List.empty
    )

    dataSourceConfigs.sqlStatementExecutionErrors shouldBe empty
    dataSourceConfigs shouldBe expectedResult
  }

  test("extracts configuration from tables outside of builtin catalog and database") {
    val statementsStr = """
       |CREATE CATALOG someCatalog WITH (
       |  'type' = 'generic_in_memory'
       |);
       |
       |CREATE DATABASE someCatalog.someDatabase;
       |
       |CREATE TABLE someCatalog.someDatabase.testTable
       |(
       |    someString  STRING
       |) WITH (
       |      'connector' = 'datagen'
       |);""".stripMargin

    val statements        = SqlStatementReader.readSql(statementsStr)
    val extractionResults = DataSourceSqlExtractor.extractTablesFromFlinkRuntime(statements)

    extractionResults.sqlStatementExecutionErrors shouldBe empty
    extractionResults.tableDefinitions shouldBe List(
      DataSourceTableDefinition("testTable", Typed.record(Map("someString" -> Typed[String])))
    )
  }

  test("returns errors for statements that cannot be executed") {
    invalidSqlStatements.foreach { invalidStatement =>
      val parsedStatement   = SqlStatementReader.readSql(invalidStatement)
      val extractionResults = DataSourceSqlExtractor.extractTablesFromFlinkRuntime(parsedStatement)

      extractionResults.sqlStatementExecutionErrors.size shouldBe 1
      extractionResults.tableDefinitions shouldBe empty
    }
  }

}

object SqlTestData {

  val invalidSqlStatements: List[String] = List(
    """|CREATE TABLE testTable
       |(
       |    someString  STRING
       |) WITH (
       |      'connector' = 'datagen
       |);""".stripMargin, // no closing quote
    """|CREATE TABLE testTable
        |(
        |    someString  STRING
        |)
        |;""".stripMargin, // no WITH clause
    """|CREATE TABLE testTable
        |(
        |    someString  STRING
        |) WITH (
        |      'connector' = ''
        |);""".stripMargin, // empty string connector - does not reach the dedicated error because fails earlier
    """|CREATE TABLE test-table
         |(
         |    someString  STRING
         |) WITH (
         |      'connector' = 'datagen'
         |);""".stripMargin, // invalid table name
    """|CREATE TABLE somedb.testTable
         |(
         |    someString  STRING
         |) WITH (
         |      'connector' = 'datagen'
         |);""".stripMargin, // trying to create a table under non-existing database
  )

  object SimpleTypesTestCase {

    val tableName = "testTable"
    val connector = "filesystem"

    val sqlStatement: String =
      s"""|CREATE TABLE testTable
         |(
         |    someString  STRING,
         |    someVarChar VARCHAR(150),
         |    someInt     INT
         |) WITH (
         |      'connector' = '$connector'
         |);""".stripMargin

    val schemaTypingResult: TypingResult = Typed.record(
      Map(
        "someString"  -> Typed[String],
        "someVarChar" -> Typed[String],
        "someInt"     -> Typed[Integer],
      )
    )

  }

}
