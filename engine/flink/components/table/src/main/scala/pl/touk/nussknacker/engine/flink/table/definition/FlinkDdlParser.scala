package pl.touk.nussknacker.engine.flink.table.definition

import cats.data.ValidatedNel
import cats.implicits._
import org.apache.calcite.config.Lex
import org.apache.calcite.sql.{SqlNode, SqlNodeList}
import org.apache.calcite.sql.parser.{SqlParser => CalciteSqlParser}
import org.apache.calcite.util.Util
import org.apache.flink.sql.parser.ddl.{SqlCreateCatalog, SqlCreateTable, SqlCreateTableAs, SqlTableOption}
import org.apache.flink.sql.parser.impl.FlinkSqlParserImpl
import org.apache.flink.sql.parser.validate.FlinkSqlConformance
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinition.FlinkSqlDdlStatement
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinition.FlinkSqlDdlStatement._
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinitionCreationError.FlinkDdlParseError
import pl.touk.nussknacker.engine.flink.table.definition.FlinkDataDefinitionCreationError.FlinkDdlParseError._

import scala.jdk.CollectionConverters._
import scala.util.Try

/*
   This parser parses statements and validates them on a very basic level.
   It does the following:
   - parses the string as a statement list, assuming a semicolon separator
   - returns errors for basic syntax errors like unescaped keywords, trailing commas, missing closing quotes
   - returns errors for missing `connector` option for CREATE TABLE and `type` option for CREATE CATALOG statements
   - allows only 3 types of statements:
    - CREATE CATALOG https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/table/sql/create/#create-catalog
    - CREATE TABLE https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/table/sql/create/#create-table
    - CREATE TABLE LIKE https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/table/sql/create/#like
    and returns error if any other are used

   It does not:
   - check whether a connector / format / catalog type is used at all
   - check validity of types - nonexisting types will be parsed without error
   - check whether a CREATE TABLE LIKE refers to a table that is defined previously
   - connect to the defined external data sources

   Some of these checks can be easily added like deeper syntax validations.
   TODO: add deeper syntax validations
 */
object FlinkDdlParser {

  def parse(sqlStatements: String): ValidatedNel[FlinkDdlParseError, List[FlinkSqlDdlStatement]] =
    parseSql(sqlStatements).andThen(extractDefinitions)

  def parseUnsafe(sqlStatements: String): List[FlinkSqlDdlStatement] =
    parse(sqlStatements).fold(e => throw e.head, identity)

  private def parseSql(sql: String): ValidatedNel[FlinkDdlParseError, List[SqlNode]] = {
    val parser = CalciteSqlParser.create(sql, sqlParserConfig)
    Try(parser.parseStmtList()).toEither.left
      .map(ParseError(_))
      .map(_.asScala.toList)
      .toValidatedNel
  }

  private def extractDefinitions(sql: List[SqlNode]): ValidatedNel[FlinkDdlParseError, List[FlinkSqlDdlStatement]] = {
    sql.traverse {
      case catalog: SqlCreateCatalog => {
        val options = extractOptions(catalog.getPropertyList)
        options
          .find(_.key == "type")
          .toValidNel(MissingCatalogTypeOption(catalog.catalogName(), options))
          .map(_.value)
          .map { catalogType =>
            CreateCatalog(
              name = CatalogName(catalog.catalogName()),
              options = extractOptions(catalog.getPropertyList),
              catalogType = CatalogType(catalogType)
            )
          }
      }
      // Create Table As (CTAS) performs an insert from another table.
      // Doc: https://nightlies.apache.org/flink/flink-docs-master/docs/dev/table/sql/create/#as-select_statement
      // Since these definitions are meant only for data source declarations, inserts are not allowed.
      // To achieve similar functionality, users should run a separate insert statement.
      case ctas: SqlCreateTableAs => UnallowedStatement(SqlString(ctas.toLinuxString)).invalidNel
      case table: SqlCreateTable => {
        val sqlString = SqlString(table.toLinuxString)
        extractOptions(table.getPropertyList)
          .find(_.key == "connector")
          .toValidNel(MissingConnectorOption(sqlString))
          .map(_.value)
          .map { connector =>
            CreateTable(sqlString, Connector(connector))
          }
      }
      case other => UnallowedStatement(SqlString(other.toLinuxString)).invalidNel
    }
  }

  private def extractOptions(properties: SqlNodeList): List[SqlOption] = {
    properties.getList.asScala.toList.map { case option: SqlTableOption =>
      SqlOption(key = option.getKeyString, value = option.getValueString)
    }
  }

  // Copied from body of Flink's internal org.apache.flink.table.planner.delegation.PlannerContext.getSqlParserConfig()
  private val sqlParserConfig = CalciteSqlParser
    .config()
    .withParserFactory(FlinkSqlParserImpl.FACTORY)
    .withConformance(FlinkSqlConformance.DEFAULT)
    .withLex(Lex.JAVA)
    .withIdentifierMaxLength(256);

  private implicit class SqlNodeExt(sqlNode: SqlNode) {

    def toLinuxString: String =
      if (Util.LINE_SEPARATOR != "\n") Util.toLinux(sqlNode.toString)
      else sqlNode.toString

  }

}
