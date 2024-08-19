package pl.touk.nussknacker.sql.db.schema

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}

import java.io.BufferedReader
import java.sql.{Clob, ResultSet, ResultSetMetaData}
import java.time.{Instant, LocalDate, LocalTime}
import java.{sql, util}
import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.util.Using

object ColumnDefinition {

  private lazy val sqlTypingMap = Map(
    classOf[sql.Array].getName     -> Typed.typedClass(classOf[util.List[Any]]),
    classOf[sql.Time].getName      -> Typed.typedClass(classOf[LocalTime]),
    classOf[sql.Date].getName      -> Typed.typedClass(classOf[LocalDate]),
    classOf[sql.Timestamp].getName -> Typed.typedClass(classOf[Instant]),
    classOf[sql.Clob].getName      -> Typed.typedClass(classOf[String]),
  )

  def apply(columnNo: Int, resultMeta: ResultSetMetaData): ColumnDefinition =
    ColumnDefinition(
      name = resultMeta.getColumnName(columnNo),
      typing = supportedTypeTypingResult(resultMeta.getColumnClassName(columnNo))
    )

  def apply(typing: (String, String)): ColumnDefinition =
    ColumnDefinition(
      name = typing._1,
      typing = supportedTypeTypingResult(typing._2)
    )

  private def supportedTypeTypingResult(className: String): TypingResult =
    sqlTypingMap.getOrElse(className, Typed.typedClass(Class.forName(className)))
}

final case class ColumnDefinition(
    name: String,
    typing: TypingResult
) {

  def extractValue(resultSet: ResultSet): Any = {
    // we could here use method resultSet.getObject(Int) and pass column number as argument
    // but in case of ignite db it is not certain which column index corresponds to which column.
    val value = resultSet.getObject(name)
    Option(value)
      .map(mapValueToSupportedType)
      .getOrElse(value)
  }

  private def mapValueToSupportedType(value: Any): Any = value match {
    case v: sql.Array     => readArray(v)
    case v: sql.Time      => v.toLocalTime
    case v: sql.Date      => v.toLocalDate
    case v: sql.Timestamp => v.toInstant
    case v: sql.Clob      => readClob(v)
    case _                => value
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

  @tailrec private def readFromStream(
      br: BufferedReader,
      acc: ArrayBuffer[String] = new ArrayBuffer[String]()
  ): String = {
    val string = br.readLine()
    if (string == null) {
      acc.mkString(System.lineSeparator)
    } else {
      acc.append(string)
      readFromStream(br, acc)
    }
  }

}
