package pl.touk.nussknacker.sql.db.schema

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}

import java.sql.ResultSetMetaData

object ColumnDefinition {
  def apply(columnNo: Int, resultMeta: ResultSetMetaData): ColumnDefinition =
    ColumnDefinition(
      no = columnNo,
      name = resultMeta.getColumnName(columnNo),
      typing = Typed(Class.forName(resultMeta.getColumnClassName(columnNo)))
    )

  def apply(columnNo: Int, typing: (String, TypingResult)): ColumnDefinition =
    ColumnDefinition(
      no = columnNo,
      name = typing._1,
      typing = typing._2
    )
}

case class ColumnDefinition(no: Int, name: String, typing: TypingResult)
