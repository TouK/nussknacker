package pl.touk.nussknacker.engine.flink.table.extractor

import org.apache.flink.table.api.DataTypes
import org.apache.flink.types.Row
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.table.TableTestCases.SimpleTable
import pl.touk.nussknacker.engine.flink.table._
import pl.touk.nussknacker.engine.flink.table.extractor.TableExtractorTest.invalidSqlStatements

class TableExtractorTest extends AnyFunSuite with Matchers with TableDrivenPropertyChecks {

  test("extracts configuration from valid sql statement") {
    val statements        = SqlStatementReader.readSql(SimpleTable.sqlStatement)
    val dataSourceConfigs = TableExtractor.extractTablesFromFlinkRuntime(statements)

    val expectedResult = TableExtractorResult(
      tableDefinitions = List(SimpleTable.tableDefinition),
      sqlStatementExecutionErrors = List.empty
    )

    dataSourceConfigs.sqlStatementExecutionErrors shouldBe empty
    dataSourceConfigs shouldBe expectedResult
  }

  test("extracts configuration from tables outside of builtin catalog and database") {
    val tableName = "testTable2"
    val statementsStr = s"""
       |CREATE CATALOG someCatalog WITH (
       |  'type' = 'generic_in_memory'
       |);
       |
       |CREATE DATABASE someCatalog.someDatabase;
       |
       |CREATE TABLE someCatalog.someDatabase.$tableName
       |(
       |    someString  STRING
       |) WITH (
       |      'connector' = 'datagen'
       |);""".stripMargin

    val statements        = SqlStatementReader.readSql(statementsStr)
    val extractionResults = TableExtractor.extractTablesFromFlinkRuntime(statements)

    extractionResults.sqlStatementExecutionErrors shouldBe empty
    extractionResults.tableDefinitions shouldBe List(
      TableDefinition(
        tableName,
        typingResult = Typed.record(Map("someString" -> Typed[String]), Typed.typedClass[Row]),
        columns = List(
          ColumnDefinition("someString", Typed[String], DataTypes.STRING())
        )
      )
    )
  }

  test("returns errors for statements that cannot be executed") {
    invalidSqlStatements.foreach { invalidStatement =>
      val parsedStatement   = SqlStatementReader.readSql(invalidStatement)
      val extractionResults = TableExtractor.extractTablesFromFlinkRuntime(parsedStatement)

      extractionResults.sqlStatementExecutionErrors.size shouldBe 1
      extractionResults.tableDefinitions shouldBe empty
    }
  }

}

object TableExtractorTest {

  private val invalidSqlStatements: List[String] = List(
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

}
