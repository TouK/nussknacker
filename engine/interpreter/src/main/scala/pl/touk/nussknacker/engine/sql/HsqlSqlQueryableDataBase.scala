package pl.touk.nussknacker.engine.sql

import java.sql._
import java.util.UUID
import com.typesafe.scalalogging.LazyLogging
import org.hsqldb.jdbc.JDBCDriver
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}
import pl.touk.nussknacker.engine.api.typed.{TypedMap, TypedObjectDefinition, typing}

import scala.collection.mutable
import scala.util.Using
import scala.util.control.Exception._


/** This class is *not* thread safe. One connection is used to handle all operations
  the idea is that we prepare all tables, and compile all queries during (lazy) initialization
  Afterwards query consists of three steps:
  - insert (in batch) all data
  - perform query
  - rollback transaction to reuse connection and not care about deleting/truncating tables
*/
class HsqlSqlQueryableDataBase(query: String, tables: Map[String, ColumnModel]) extends SqlQueryableDataBase with LazyLogging {

  import HsqlSqlQueryableDataBase._

  private val connection: Connection = {
    val url = s"jdbc:hsqldb:mem:${UUID.randomUUID()};shutdown=true;allow_empty_batch=true"
    //TODO: sometimes in tests something deregisters HsqlSql driver...
    if (catching(classOf[SQLException]).opt( DriverManager.getDriver(url)).isEmpty) {
      DriverManager.registerDriver(new JDBCDriver)
    }
    DriverManager.getConnection(url, "SA", "")
  }

  init()

  private def init(): Unit = {
    connection.setAutoCommit(false)
    tables.map { table =>
      createTableQuery(table._1, table._2)
    } foreach { query =>
      logger.debug(query)
      Using.resource(connection.prepareStatement(query))(_.execute())
    }
  }

  private lazy val insertTableStatements: Map[String, PreparedStatement] = tables.map {
    case (name, model) =>
      val query = insertTableQuery(name, model)
      (name, connection.prepareStatement(query))
  }

  private lazy val queryStatement = connection.prepareStatement(query)

  private def insertTables(tables: Map[String, Table]): Unit = {
    tables.foreach { case (name, Table(_, rows)) =>
      insertTable(name, rows)
    }
  }

  private def insertTable(name: String, rows: List[List[Any]]): Unit = {
    val statement = insertTableStatements(name)
    rows.foreach(insertRow(statement, _))
    statement.executeBatch()
  }

  private def insertRow(statement: PreparedStatement, row: List[Any]): Unit = {
    logger.debug(query)
    row.zip(Stream.from(1)).foreach { case (obj, idx) =>
      logger.trace(s"Setting query parameter. ${parameterDetails(statement.getParameterMetaData, idx, obj)}")
      try {
        statement.setObject(idx, obj)
      } catch {
        case e: SQLSyntaxErrorException =>
          logger.error(s"Error during setting query parameter. ${parameterDetails(statement.getParameterMetaData, idx, obj)}")
          throw e
      }
    }
    statement.addBatch()
  }

  private def parameterDetails(params: ParameterMetaData, idx: Int, obj: Any): String = {
    val sqlParamType = params.getParameterTypeName(idx)
    val expectedClass = params.getParameterClassName(idx)
    val actualClass = if (obj != null) obj.getClass.getCanonicalName else "null"
    s"Query index: $idx, sql type: $sqlParamType, expected class: $expectedClass, actualClass: $actualClass, value: $obj"
  }

  override def query(tables: Map[String, Table]): List[TypedMap] = {
    logger.trace(s"Executing query: $query with $tables")
    //
    insertTables(tables)
    val data = executeQuery(queryStatement)
    connection.rollback()
    data
  }

  override def getTypingResult: typing.TypingResult = {
    val metaData = queryStatement.getMetaData
    TypedObjectTypingResult(toTypedMapDefinition(metaData))
  }

  override def close(): Unit = {
    connection.close()
  }
}


private object HsqlSqlQueryableDataBase extends LazyLogging {
  import SqlType._
  private val str: SqlType => String = {
    case Numeric => "NUMERIC"
    case Decimal => "DECIMAL(20, 2)"
    case Varchar => "VARCHAR(50)"
    case Bool => "BIT"
    case Date => "DATETIME"
  }

  private[sql] def createTableQuery(name: String, columnModel: ColumnModel): String = {
    val columns = columnModel.columns
      .map { c =>
        s"${c.name} ${str(c.typ)}"
      }.mkString(", ")
    s"CREATE TABLE $name ($columns)"
  }

  private[sql] def insertTableQuery(name: String, columnModel: ColumnModel): String = {
    val columns = columnModel.columns.map(_.name).mkString(", ")
    val values = columnModel.columns.map(_ => "?").mkString(", ")
    s"INSERT INTO $name ($columns) VALUES ($values)"
  }

  private def executeQuery(statement: PreparedStatement): List[TypedMap] = {
    val rs = statement.executeQuery()
    val result = mutable.Buffer[TypedMap]()
    val types = toTypedMapDefinition(statement.getMetaData)
    while (rs.next()) {
      var map = Map[String, Any]()
      types.fields.foreach { case (k, typeRef) =>
        //FIXME: type conversions...
        map = map + (k -> rs.getObject(k))
      }
      result += TypedMap(map)
    }
    result.toList
  }

  private def toTypedMapDefinition(meta: ResultSetMetaData): TypedObjectDefinition = {
    val cols = (1 to meta.getColumnCount).map { idx =>
      val name = meta.getColumnLabel(idx)
      //more mappings?
      val typ = meta.getColumnType(idx) match {
        case Types.BIT | Types.TINYINT | Types.SMALLINT | Types.INTEGER
             | Types.BIGINT | Types.FLOAT | Types.REAL | Types.DOUBLE | Types.NUMERIC | Types.DECIMAL =>
          Typed[Number]
        case Types.VARCHAR | Types.LONGNVARCHAR =>
          Typed[String]
        case Types.CHAR =>
          Typed[Char]
        case Types.BOOLEAN =>
          Typed[Boolean]
        case Types.DATE | Types.TIMESTAMP =>
          Typed[java.util.Date]
        case a =>
          logger.warn(s"no type mapping for column type: $a, column: ${meta.getColumnName(idx)}")
          Typed[Any]
      }
      name -> typ
    }.toList

    TypedObjectDefinition(cols)
  }
}
