package pl.touk.nussknacker.engine.sql

import java.sql._
import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.typed.typing.TypedMapTypingResult
import pl.touk.nussknacker.engine.api.typed.{ClazzRef, TypedMap, TypedMapDefinition, typing}

import scala.collection.{immutable, mutable}

class HsqlSqlQueryableDataBase extends SqlQueryableDataBase with LazyLogging {

  import HsqlSqlQueryableDataBase._

  private val connection: Connection =
    DriverManager.getConnection(s"jdbc:hsqldb:mem:${UUID.randomUUID()};shutdown=true", "SA", "")

  override def createTables(tables: Map[String, ColumnModel]): Unit = {
    tables.map { table =>
      createTableQuery(table._1, table._2)
    } foreach { query =>
      logger.debug(query)
      val statement = connection.prepareStatement(query)
      statement.execute()
      statement.close()
    }
  }

  override def insertTables(tables: Map[String, Table]): Unit = {
    tables.foreach { case (name, Table(model, rows)) =>
      val query = insertTableQuery(name, model)
      val statement = connection.prepareStatement(query)
      rows.foreach { row =>
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
        statement.execute()
      }
      statement.close()
    }
  }

  private def parameterDetails(params: ParameterMetaData, idx: Int, obj: Any): String = {
    val sqlParamType = params.getParameterTypeName(idx)
    val expectedClass = params.getParameterClassName(idx)
    val actualClass = if (obj != null) obj.getClass.getCanonicalName else "null"
    s"Query index: $idx, sql type: $sqlParamType, expected class: $expectedClass, actualClass: $actualClass, value: $obj"
  }

  override def query(query: String): List[TypedMap] = {
    logger.debug(s"query: $query")
    val statement = connection.prepareStatement(query)
    val result = getData(statement)
    statement.close()
    result
  }

  override def getTypingResult(query: String): typing.TypingResult = {
    logger.debug(s"query for typing result: $query")
    val statement = connection.prepareStatement(query)
    val metaData = statement.getMetaData
    val result = TypedMapTypingResult(toTypedMapDefinition(metaData))
    statement.close()
    result
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

  def createTableQuery(name: String, columnModel: ColumnModel): String = {
    val columns = columnModel.columns
      .map { c =>
        s"${c.name} ${str(c.typ)}"
      }.mkString(", ")
    s"CREATE TABLE $name ($columns)"
  }

  def insertTableQuery(name: String, columnModel: ColumnModel): String = {
    val columns = columnModel.columns.map(_.name).mkString(", ")
    val values = columnModel.columns.map(_ => "?").mkString(", ")
    s"INSERT INTO $name ($columns) VALUES ($values)"
  }

  def getData(statement: PreparedStatement): List[TypedMap] = {
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

  private def toTypedMapDefinition(meta: ResultSetMetaData): TypedMapDefinition = {
    val cols = (1 to meta.getColumnCount).map { idx =>
      val name = meta.getColumnLabel(idx)
      //more mappings?
      val typ = meta.getColumnType(idx) match {
        case Types.BIT | Types.TINYINT | Types.SMALLINT | Types.INTEGER
             | Types.BIGINT | Types.FLOAT | Types.REAL | Types.DOUBLE | Types.NUMERIC | Types.DECIMAL =>
          ClazzRef[Number]
        case Types.VARCHAR | Types.LONGNVARCHAR =>
          ClazzRef[String]
        case Types.CHAR =>
          ClazzRef[Char]
        case Types.BOOLEAN =>
          ClazzRef[Boolean]
        case a =>
          logger.warn(s"no type mapping for column type: $a")
          ClazzRef[Any]
      }
      name -> typ
    }.toMap

    TypedMapDefinition(cols)
  }
}
