package pl.touk.nussknacker.sql.db.schema

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}

import java.io.BufferedReader
import java.sql.Clob
import java.time.{Instant, LocalDate, LocalTime}
import java.{sql, util}
import scala.util.Using

final case class ConvertedToSupportTypeTypingResult(typing: TypingResult, typeRemappingFunction: Any => Any)

object ConvertedToSupportTypeTypingResult {

  private lazy val sqlTypingMap = Map(
    classOf[sql.Array].getName     -> Typed.typedClass(classOf[util.List[Any]]),
    classOf[sql.Time].getName      -> Typed.typedClass(classOf[LocalTime]),
    classOf[sql.Date].getName      -> Typed.typedClass(classOf[LocalDate]),
    classOf[sql.Timestamp].getName -> Typed.typedClass(classOf[Instant]),
    classOf[sql.Clob].getName      -> Typed.typedClass(classOf[String]),
  )

  def apply(className: String): ConvertedToSupportTypeTypingResult =
    ConvertedToSupportTypeTypingResult(
      typing = sqlTypingMap.getOrElse(className, Typed.typedClass(Class.forName(className))),
      typeRemappingFunction = typeRemappingFunction
    )

  private def typeRemappingFunction(value: Any): Any = value match {
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
    Using.resource(new BufferedReader(v.getCharacterStream)) { br =>
      LazyList
        .continually(br.readLine())
        .takeWhile(_ != null)
        .mkString("\n")
    }
  }

}
