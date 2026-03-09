package pl.touk.nussknacker.engine.flink.table.io.definition

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.flink.table.FlinkSqlTableTestCases
import pl.touk.nussknacker.engine.flink.table.io.definition.FlinkDataDefinitionCreationError._
import pl.touk.nussknacker.engine.flink.table.io.definition.FlinkDdlParserTest.syntacticallyInvalidSqlStatements
import pl.touk.nussknacker.engine.flink.table.io.definition.FlinkSqlDdlStatement._
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

class FlinkDdlParserTest extends AnyFunSuite with Matchers {

  test("parse valid create table statements") {
    FlinkDdlParser.parse(FlinkSqlTableTestCases.unboundedKafkaTable) shouldBe Valid(
      List(
        CreateTable(
          TableName("my_kafka_table"),
          SqlString(FlinkSqlTableTestCases.unboundedKafkaTableFormatted),
          Connector("kafka")
        )
      )
    )
  }

  test("parse valid create catalog statement") {
    FlinkDdlParser.parse(FlinkSqlTableTestCases.PostgresCatalog.postgresCatalog) shouldBe Valid(
      List(FlinkSqlTableTestCases.PostgresCatalog.postgresCatalogParsed)
    )
  }

  test("parse multiple valid create statements") {
    FlinkDdlParser.parse(
      s"${FlinkSqlTableTestCases.unboundedKafkaTable};\n" +
        s"${FlinkSqlTableTestCases.unboundedDatagenTable};\n" +
        s"${FlinkSqlTableTestCases.PostgresCatalog.postgresCatalog}"
    ) shouldBe Valid(
      List(
        CreateTable(
          TableName("my_kafka_table"),
          SqlString(FlinkSqlTableTestCases.unboundedKafkaTableFormatted),
          Connector("kafka")
        ),
        CreateTable(
          TableName("datagen_table"),
          SqlString(FlinkSqlTableTestCases.unboundedDatagenTableFormatted),
          Connector("datagen")
        ),
        FlinkSqlTableTestCases.PostgresCatalog.postgresCatalogParsed
      )
    )
  }

  test("parses semantically invalid but parseable statements") {
    val sqlStatements = FlinkDdlParserTest.semanticallyIllegalButParseableStatements.mkString(";\n")
    FlinkDdlParser.parse(sqlStatements) should matchPattern { case Valid(_) => }
  }

  test("return error for syntactically invalid statements") {
    syntacticallyInvalidSqlStatements.foreach { s =>
      FlinkDdlParser.parse(s) should matchPattern { case Invalid(NonEmptyList(ParseError(_), Nil)) => }
    }
  }

  test("return multiple errors for multiple unallowed statements") {
    val sqlStatements = FlinkDdlParserTest.unallowedSqlStatements.mkString(";\n")
    FlinkDdlParser.parse(sqlStatements) should matchPattern {
      case Invalid(NonEmptyList(UnallowedStatement(_), List(UnallowedStatement(_)))) =>
    }
  }

  test("return error for missing connector option") {
    val sqlStatement = "CREATE TABLE testTable (str STRING) WITH ()"
    FlinkDdlParser.parse(sqlStatement) should matchPattern {
      case Invalid(NonEmptyList(MissingConnectorOption(_), Nil)) =>
    }
  }

  test("return error for missing catalog type") {
    val sqlStatement = "CREATE CATALOG testCatalog WITH ()"
    FlinkDdlParser.parse(sqlStatement) should matchPattern {
      case Invalid(NonEmptyList(MissingCatalogTypeOption(_, _), Nil)) =>
    }
  }

}

object FlinkDdlParserTest {

  private val unallowedSqlStatements: List[String] = List(
    "DROP TABLE Orders",
    """|CREATE TABLE test_ctas_table (
       |    id INT
       |) WITH (
       |    'connector' = 'blackhole'
       |)
       |AS  SELECT id FROM source_table
       |""".stripMargin
  )

  private val semanticallyIllegalButParseableStatements: List[String] = List(
    s"""|CREATE TABLE testTable1 (
        |    someString NON_EXISTANT_TYPE,
        |    someRaw    RAW('NON_EXISTANT_PARAMETER', 'NON_EXISTANT_PARAMETER2')
        |) WITH (
        |    'connector' = 'datagen'
        |)""".stripMargin,
    s"""|CREATE TABLE testTable2 (
        |    duplicatedColumnName STRING,
        |    duplicatedColumnName STRING
        |) WITH (
        |    'connector' = 'datagen'
        |)""".stripMargin,
    s"""|CREATE TABLE testTableWithEmptyConnector (
        |    col STRING
        |) WITH (
        |    'connector' = ''
        |)""".stripMargin,
    s"""|CREATE TABLE testTableWithInvalidConnector (
        |    col STRING
        |) WITH (
        |    'connector' = 'non_existant_connector'
        |)""".stripMargin
  )

  private val syntacticallyInvalidSqlStatements: List[String] = List(
    """|CREATE TABLE testTable (
       |    someString  STRING
       |) WITH (
       |      'connector' = 'datagen
       |)""".stripMargin, // no closing quote
    """|CREATE TABLE testTable (
       |    someString STRING,
       |) WITH (
       |    'connector' = 'datagen'
       |)""".stripMargin, // trailing comma
    """|CREATE TABLE testTable (
       |    value STRING
       |) WITH (
       |    'connector' = 'datagen'
       |)""".stripMargin, // unescaped `value` keyword
    """|CREATE TABLE test-table
       |(
       |    someString  STRING
       |) WITH (
       |      'connector' = 'datagen'
       |);""".stripMargin, // invalid table name
  )

}
