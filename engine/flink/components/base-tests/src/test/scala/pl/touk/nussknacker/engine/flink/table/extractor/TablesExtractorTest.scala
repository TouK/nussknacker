package pl.touk.nussknacker.engine.flink.table.extractor

import org.apache.flink.table.api.DataTypes
import org.apache.flink.table.catalog.{Column, ResolvedSchema}
import org.scalatest.LoneElement
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.flink.table.utils.DataTypesConversions._
import pl.touk.nussknacker.engine.flink.table.TableTestCases.SimpleTable
import pl.touk.nussknacker.engine.flink.table._
import pl.touk.nussknacker.engine.flink.table.extractor.TablesExtractorTest.invalidSqlStatements
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import scala.jdk.CollectionConverters._

class TablesExtractorTest
    extends AnyFunSuite
    with Matchers
    with LoneElement
    with ValidatedValuesDetailedMessage
    with TableDrivenPropertyChecks {

  test("extracts configuration from valid sql statement") {
    val statements        = SqlStatementReader.readSql(SimpleTable.sqlStatement)
    val tablesDefinitions = TablesExtractor.extractTablesFromFlinkRuntime(statements).validValue
    val tableDefinition   = tablesDefinitions.loneElement
    val sourceRowType     = tableDefinition.sourceRowDataType.toLogicalRowTypeUnsafe
    sourceRowType.getFieldNames.asScala shouldBe List(
      "someString",
      "someVarChar",
      "someInt",
      "someIntComputed",
      "file.name"
    )
    sourceRowType.getTypeAt(0) shouldEqual DataTypes.STRING().getLogicalType
    sourceRowType.getTypeAt(1) shouldEqual DataTypes.VARCHAR(150).getLogicalType
    sourceRowType.getTypeAt(2) shouldEqual DataTypes.INT().getLogicalType
    sourceRowType.getTypeAt(3) shouldEqual DataTypes.INT().getLogicalType
    sourceRowType.getTypeAt(4) shouldEqual DataTypes.STRING().notNull().getLogicalType

    tableDefinition.sinkRowDataType.toLogicalRowTypeUnsafe.getFieldNames.asScala shouldBe List(
      "someString",
      "someVarChar",
      "someInt",
      "file.name"
    )
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
    val tablesDefinitions = TablesExtractor.extractTablesFromFlinkRuntime(statements).validValue

    tablesDefinitions.loneElement shouldBe TableDefinition(
      tableName,
      ResolvedSchema.of(Column.physical("someString", DataTypes.STRING()))
    )
  }

  test("returns errors for statements that cannot be executed") {
    invalidSqlStatements.foreach { invalidStatement =>
      val parsedStatement             = SqlStatementReader.readSql(invalidStatement)
      val sqlStatementExecutionErrors = TablesExtractor.extractTablesFromFlinkRuntime(parsedStatement).invalidValue

      sqlStatementExecutionErrors.size shouldBe 1
    }
  }

}

object TablesExtractorTest {

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
