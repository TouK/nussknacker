package pl.touk.nussknacker.engine.sql

import java.sql._
import java.util.UUID

import pl.touk.nussknacker.engine.api.typed.typing.TypedMapTypingResult
import pl.touk.nussknacker.engine.api.typed.{ClazzRef, TypedMap, TypedMapDefinition, typing}

import scala.collection.{immutable, mutable}

class HsqlSqlQueryableDataBase extends SqlQueryableDataBase {

  import HsqlSqlQueryableDataBase._

  private val connection: Connection =
    DriverManager.getConnection(s"jdbc:hsqldb:mem:${UUID.randomUUID()};shutdown=true", "SA", "")

  override def createTables(tables: Map[String, ColumnModel]): Unit = {
    tables.map { table =>
      createTableQuery(table._1, table._2)
    } foreach { query =>
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
        row.zipWithIndex.foreach { case(obj, idx) =>
          statement.setObject(idx + 1, obj)
        }
        statement.execute()
      }
      statement.close()
    }
  }

  override def query(query: String): List[TypedMap] = {
    val statement = connection.prepareStatement(query)
    val result = getData(statement)
    statement.close()
    result
  }

  override def getTypingResult(query: String): typing.TypingResult = {
    val statement = connection.prepareStatement(query)
    val result = TypedMapTypingResult(
      toTypedMapDefinition(
        statement.getMetaData
      )
    )
    statement.close()
    result
  }

  override def close(): Unit = {
    connection.close()
  }
}


private object HsqlSqlQueryableDataBase {
  import SqlType._
  private val str: SqlType => String = {
    case Numeric => "NUMERIC"
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

  private def toTypedMapDefinition(meta: ResultSetMetaData) = {
    val cols = (1 to meta.getColumnCount).map { idx =>
      val name = meta.getColumnName(idx)
      val typ = meta.getColumnType(idx) match {
        case Types.NUMERIC => ClazzRef[Number]
        case Types.VARCHAR => ClazzRef[String]
        case Types.BOOLEAN => ClazzRef[Boolean]

        case _ => ClazzRef[Any]
      }
      name -> typ
    }.toMap

    TypedMapDefinition(cols)
  }
}
