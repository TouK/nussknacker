package pl.touk.nussknacker.engine.flink.table.io.definition

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.DataTypes
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment
import org.scalatest.{BeforeAndAfterAll, LoneElement, Outcome}
import org.scalatest.funspec.FixtureAnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.table.io.definition.discovery.TableDiscoveryImpl
import pl.touk.nussknacker.engine.flink.table.typing.DataTypesExtensions.DataTypeExtension
import pl.touk.nussknacker.test.{PatientScalaFutures, ValidatedValuesDetailedMessage}

import scala.jdk.CollectionConverters._

class TablesDefinitionDiscoveryTest
    extends FixtureAnyFunSpec
    with Matchers
    with LoneElement
    with ValidatedValuesDetailedMessage
    with TableDrivenPropertyChecks
    with PatientScalaFutures
    with BeforeAndAfterAll {

  override protected type FixtureParam = StreamExecutionEnvironment

  private val miniClusterWithServices =
    FlinkMiniClusterFactory
      .createUnitTestsMiniClusterWithServices()

  override protected def afterAll(): Unit = {
    super.afterAll()
    miniClusterWithServices.close()
  }

  override protected def withFixture(test: OneArgTest): Outcome = {
    miniClusterWithServices.withDetachedStreamExecutionEnvironment { env =>
      test(env)
    }
  }

  private def discoverTables(sql: String, sEnv: StreamExecutionEnvironment): List[TableDefinition] = {
    val parsedSql      = FlinkDdlParser.parseUnsafe(sql)
    val dataDefinition = FlinkDataDefinition.applyUnsafe(parsedSql, None)

    val env = StreamTableEnvironment.create(sEnv)

    val discovery = new TableDiscoveryImpl(List.empty)
    discovery
      .discoverTableIdentifiers(dataDefinition, env)
      .map(id => discovery.discoverTable(env, dataDefinition, id))
  }

  it("extracts table definition with correct source and sink data type") { env =>
    val sql =
      s"""|CREATE TABLE testTable
          |(
          |    someString  STRING,
          |    someVarChar VARCHAR(150),
          |    someInt     INT,
          |    someIntComputed AS someInt * 2,
          |    `file.name` STRING NOT NULL METADATA
          |) WITH (
          |    'connector' = 'filesystem',
          |    'path' = '.',
          |    'format' = 'csv'
          |);""".stripMargin
    val tableDefinition = discoverTables(sql, env).loneElement

    val sourceRowType = tableDefinition.sourceRowDataType.toLogicalRowTypeUnsafe
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

}
