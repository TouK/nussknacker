package pl.touk.nussknacker.sql.db.schema

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}

import java.{sql, util}
import java.io.BufferedReader
import java.sql.{Clob, ResultSet, ResultSetMetaData}
import java.time.{Instant, LocalDate, LocalTime}
import java.util.stream.Collectors
import scala.util.Using

final case class ColumnDefinition(
    name: String,
    typing: TypingResult,
    valueMapping: Any => Any
) {

  def extractValue(resultSet: ResultSet): Any = {
    // we could here use method resultSet.getObject(Int) and pass column number as argument
    // but in case of ignite db it is not certain which column index corresponds to which column.
    val value = resultSet.getObject(name)
    Option(value).map(valueMapping).getOrElse(value)
  }

}

object ColumnDefinition {
  private val sqlArrayClassName     = classOf[sql.Array].getName
  private val sqlTimeClassName      = classOf[sql.Time].getName
  private val sqlDateClassName      = classOf[sql.Date].getName
  private val sqlTimestampClassName = classOf[sql.Timestamp].getName
  private val sqlClobClassName      = classOf[sql.Clob].getName

  def apply(columnNo: Int, resultMeta: ResultSetMetaData): ColumnDefinition = {
    val (typingResult, valueMapping) = mapValueToSupportedType(resultMeta.getColumnClassName(columnNo))
    ColumnDefinition(
      name = resultMeta.getColumnName(columnNo),
      typing = typingResult,
      valueMapping = valueMapping
    )
  }

  def apply(typing: (String, String)): ColumnDefinition = {
    val (typingResult, valueMapping) = mapValueToSupportedType(typing._2)
    ColumnDefinition(
      name = typing._1,
      typing = typingResult,
      valueMapping = valueMapping
    )
  }

  private def mapValueToSupportedType(className: String): (TypingResult, Any => Any) = className match {
    case `sqlArrayClassName` => (Typed.typedClass(classOf[util.List[Any]]), v => readArray(v.asInstanceOf[sql.Array]))
    case `sqlTimeClassName`  => (Typed.typedClass(classOf[LocalTime]), v => v.asInstanceOf[sql.Time].toLocalTime)
    case `sqlDateClassName`  => (Typed.typedClass(classOf[LocalDate]), v => v.asInstanceOf[sql.Date].toLocalDate)
    case `sqlTimestampClassName` => (Typed.typedClass(classOf[Instant]), v => v.asInstanceOf[sql.Timestamp].toInstant)
    case `sqlClobClassName`      => (Typed.typedClass(classOf[String]), v => readClob(v.asInstanceOf[sql.Clob]))
    case _                       => (Typed.typedClass(Class.forName(className)), identity)
  }

  private def readArray(v: sql.Array): util.List[AnyRef] = {
    val result    = new util.ArrayList[AnyRef]()
    val resultSet = v.getResultSet
    while (resultSet.next()) {
      result.add(resultSet.getObject(1))
    }
    result
  }

  private def readClob(v: Clob): String = {
    Using.resource(new BufferedReader(v.getCharacterStream))(br => readFromStream(br))
  }

  private def readFromStream(br: BufferedReader): String =
    br.lines().collect(Collectors.joining(System.lineSeparator()))
}
