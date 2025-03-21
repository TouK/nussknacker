package pl.touk.nussknacker.engine.flink.table.definition

import cats.implicits.catsSyntaxValidatedId
import org.apache.flink.configuration.Configuration
import org.apache.flink.table.api.DataTypes
import org.apache.flink.table.catalog._
import org.scalatest.LoneElement
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.flink.table.FlinkSqlTableTestCases
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinition.EmptyDataDefinitionConfiguration
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
    FlinkDataDefinition.apply(None, None) shouldBe EmptyDataDefinitionConfiguration.invalidNel
  }

  test("extracts configuration from valid sql statement") {
    val flinkDataDefinition = FlinkDataDefinition.applyUnsafe(Some(FlinkSqlTableTestCases.allColumnTypesTable), None)
    val discovery           = TablesDefinitionDiscovery.prepareDiscovery(flinkDataDefinition).validValue
    val tablesDefinitions   = discovery.listTables
    val tableDefinition     = tablesDefinitions.loneElement
    val sourceRowType       = tableDefinition.sourceRowDataType.toLogicalRowTypeUnsafe
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

  test("returns errors for statements that cannot be executed") {
    invalidSqlStatements.foreach { invalidStatement =>
      val flinkDataDefinition         = FlinkDataDefinition.applyUnsafe(Some(invalidStatement), None)
      val sqlStatementExecutionErrors = TablesDefinitionDiscovery.prepareDiscovery(flinkDataDefinition).invalidValue

      sqlStatementExecutionErrors.size shouldBe 1
    }
  }

  test("use catalog configuration in data definition") {
    val catalogConfiguration = Configuration.fromMap(Map("type" -> StubbedCatalogFactory.catalogName).asJava)
    val flinkDataDefinition  = FlinkDataDefinition.applyUnsafe(None, Some(catalogConfiguration))

    val discovery = TablesDefinitionDiscovery.prepareDiscovery(flinkDataDefinition).validValue

    val tableDefinition = discovery.listTables.loneElement

    tableDefinition.tableId.toString shouldBe s"`_nu_catalog`." +
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
       |)
       |;""".stripMargin, // no WITH clause
    """|CREATE TABLE testTable
       |(
       |    someString  STRING
       |) WITH (
       |      'connector' = ''
       |);""".stripMargin, // empty string connector - does not reach the dedicated error because fails earlier
    """|CREATE TABLE somedb.testTable
       |(
       |    someString  STRING
       |) WITH (
       |      'connector' = 'datagen'
       |);""".stripMargin, // trying to create a table under non-existing database
  )

}
