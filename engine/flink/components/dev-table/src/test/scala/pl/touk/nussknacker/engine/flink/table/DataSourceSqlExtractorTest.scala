package pl.touk.nussknacker.engine.flink.table

import org.apache.flink.table.api.DataTypes
import org.scalatest.Inside.inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor2}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.table.TestData.{
  SimpleTypesTestCase,
  invalidSqlStatementsAndNonCreateTableStatements
}
import pl.touk.nussknacker.engine.flink.table.extractor._

class DataSourceSqlExtractorTest extends AnyFunSuite with Matchers with TableDrivenPropertyChecks {

  test("extracts configuration from valid sql statement") {
    // TODO: do a table driven check to cover more cases
    val statements        = SqlStatementReader.readSql(SimpleTypesTestCase.sqlStatement)
    val dataSourceConfigs = DataSourceSqlExtractor.extractTablesFromFlinkRuntime(statements)

    dataSourceConfigs.headOption match {
      case Some(value) =>
        value match {
          case Right(config) => config shouldBe SimpleTypesTestCase.expectedConfig
          case Left(e)       => fail(s"Table contained error: $e")
        }
      case None => fail("No table was extracted from")
    }
  }

  test("return correct error type for invalid sql statement or queries") {
    forAll(invalidSqlStatementsAndNonCreateTableStatements) { case (statement, expectedErrorType) =>
      val statements        = SqlStatementReader.readSql(statement)
      val dataSourceConfigs = DataSourceSqlExtractor.extractTablesFromFlinkRuntime(statements)

      dataSourceConfigs.headOption should matchPattern {
        case Some(Left(SqlExtractorError(`expectedErrorType`, _, _))) =>
      }
    }
  }

  test(
    "return correct error type for valid sql statements that are queries or operating outside of controlled context"
  ) {
    val statementsStr = """
       |CREATE DATABASE someDatabase;
       |
       |CREATE TABLE someDatabase.testTable
       |(
       |    someString  STRING
       |) WITH (
       |      'connector' = 'datagen'
       |);""".stripMargin

    val statements        = SqlStatementReader.readSql(statementsStr)
    val dataSourceConfigs = DataSourceSqlExtractor.extractTablesFromFlinkRuntime(statements)

    inside(dataSourceConfigs) { case first :: second :: Nil =>
      inside(first) { case Left(SqlExtractorError(TableNotCreatedOrCreatedOutsideOfContext, _, _)) =>
      }
      inside(second) { case Left(SqlExtractorError(TableNotCreatedOrCreatedOutsideOfContext, _, _)) =>
      }
    }
  }

}

object TestData {

  import org.scalatest.prop.TableDrivenPropertyChecks._

  val invalidSqlStatementsAndNonCreateTableStatements: TableFor2[String, SqlExtractionErrorType] = Table(
    ("statement", "expectedErrorType"),
    (
      """|CREATE TABLE testTable
       |(
       |    someString  STRING
       |) WITH (
       |      'connector' = 'datagen
       |);""".stripMargin,
      StatementNotExecuted
    ), // no closing quote
    (
      """|CREATE TABLE testTable
        |(
        |    someString  STRING
        |)
        |;""".stripMargin,
      StatementNotExecuted
    ), // no WITH clause
    (
      """|CREATE TABLE testTable
        |(
        |    someString  STRING
        |) WITH (
        |      'connector' = ''
        |);""".stripMargin,
      StatementNotExecuted
    ), // empty string connector - does not reach the dedicated error because fails earlier
    (
      """|CREATE TABLE test-table
         |(
         |    someString  STRING
         |) WITH (
         |      'connector' = 'datagen'
         |);""".stripMargin,
      StatementNotExecuted
    ), // invalid table name
    (
      """|CREATE TABLE somedb.testTable
         |(
         |    someString  STRING
         |) WITH (
         |      'connector' = 'datagen'
         |);""".stripMargin,
      StatementNotExecuted
    ), // trying to create a table under non-existing database
    (
      """|CREATE DATABASE somecatalog;""".stripMargin,
      TableNotCreatedOrCreatedOutsideOfContext
    )
  )

  object SimpleTypesTestCase {

    val sqlStatement: String =
      """|CREATE TABLE testTable
         |(
         |    someString  STRING,
         |    someVarChar VARCHAR(150),
         |    someInt     INT
         |) WITH (
         |      'connector' = 'datagen'
         |);""".stripMargin

    val schema: DataSourceSchema = DataSourceSchema(
      columns = List(
        Column("someString", DataTypes.STRING()),
        Column("someVarChar", DataTypes.VARCHAR(150)),
        Column("someInt", DataTypes.INT())
      )
    )

    val typingResult: typing.TypedObjectTypingResult = Typed.record(
      Map(
        "someString"  -> Typed[String],
        "someVarChar" -> Typed[String],
        "someInt"     -> Typed[Integer],
      )
    )

    val expectedConfig: SqlDataSourceConfig = SqlDataSourceConfig(
      "testTable",
      "datagen",
      schema,
      typingResult,
      sqlStatement
    )

  }

}
