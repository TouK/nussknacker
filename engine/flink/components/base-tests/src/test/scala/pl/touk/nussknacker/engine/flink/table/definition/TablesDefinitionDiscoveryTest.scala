package pl.touk.nussknacker.engine.flink.table.definition

import cats.data.Validated.Invalid
import org.apache.flink.configuration.Configuration
import org.apache.flink.table.api.{DataTypes, Schema}
import org.apache.flink.table.catalog._
import org.scalatest.LoneElement
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.flink.table.TableTestCases.SimpleTable
import pl.touk.nussknacker.engine.flink.table.definition.TablesDefinitionDiscoveryTest.invalidSqlStatements
import pl.touk.nussknacker.engine.flink.table.utils.DataTypesExtensions._
import pl.touk.nussknacker.test.{PatientScalaFutures, ValidatedValuesDetailedMessage}

import scala.jdk.CollectionConverters._

class TablesDefinitionDiscoveryTest
    extends AnyFunSuite
    with Matchers
    with LoneElement
    with ValidatedValuesDetailedMessage
    with TableDrivenPropertyChecks
    with PatientScalaFutures {

  test("return error for empty flink data definition") {
    FlinkDataDefinition.create(
      sqlStatements = None,
      catalogConfigurationOpt = None,
    ) should matchPattern { case Invalid(EmptyDataDefinition) =>
    }
  }

  test("extracts configuration from valid sql statement") {
    val flinkDataDefinition = FlinkDataDefinition
      .create(
        sqlStatements = Some(SqlStatementReader.readSql(SimpleTable.sqlStatement)),
        catalogConfigurationOpt = None,
      )
      .validValue
    val discovery         = TablesDefinitionDiscovery.prepareDiscovery(flinkDataDefinition).validValue
    val tablesDefinitions = discovery.listTables
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

    val flinkDataDefinition = FlinkDataDefinition
      .create(
        sqlStatements = Some(SqlStatementReader.readSql(statementsStr)),
        catalogConfigurationOpt = None,
      )
      .validValue
    val discovery        = TablesDefinitionDiscovery.prepareDiscovery(flinkDataDefinition).validValue
    val tablesDefinition = discovery.listTables.loneElement

    tablesDefinition.tableId.toString shouldBe "`someCatalog`.`someDatabase`.`testTable2`"
    tablesDefinition.schema shouldBe ResolvedSchema.of(Column.physical("someString", DataTypes.STRING()))
  }

  test("returns errors for statements that cannot be executed") {
    invalidSqlStatements.foreach { invalidStatement =>
      val flinkDataDefinition = FlinkDataDefinition
        .create(
          sqlStatements = Some(SqlStatementReader.readSql(invalidStatement)),
          catalogConfigurationOpt = None,
        )
        .validValue
      val sqlStatementExecutionErrors = TablesDefinitionDiscovery.prepareDiscovery(flinkDataDefinition).invalidValue

      sqlStatementExecutionErrors.size shouldBe 1
    }
  }

  test("use catalog configuration in data definition") {
    val catalogConfiguration = Configuration.fromMap(Map("type" -> StubbedCatalogFactory.catalogName).asJava)
    val flinkDataDefinition  = FlinkDataDefinition.create(None, Some(catalogConfiguration)).validValue

    val discovery = TablesDefinitionDiscovery.prepareDiscovery(flinkDataDefinition).validValue

    val tableDefinition = discovery.listTables.loneElement

    tableDefinition.tableId.toString shouldBe s"`${FlinkDataDefinition.internalCatalogName}`." +
      s"`${StubbedCatalogFactory.sampleBoundedTablePath.getDatabaseName}`." +
      s"`${StubbedCatalogFactory.sampleBoundedTablePath.getObjectName}`"
    tableDefinition.schema shouldBe ResolvedSchema.of(
      Column.physical(StubbedCatalogFactory.sampleColumnName, DataTypes.STRING())
    )
  }

}

object TablesDefinitionDiscoveryTest {

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
