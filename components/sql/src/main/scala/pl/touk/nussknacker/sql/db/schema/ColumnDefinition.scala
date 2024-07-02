package pl.touk.nussknacker.sql.db.schema

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}

import java.sql.ResultSetMetaData

object ColumnDefinition {

  def apply(columnNo: Int, resultMeta: ResultSetMetaData): ColumnDefinition =
    ColumnDefinition(
      name = resultMeta.getColumnName(columnNo),
      typing = Typed(Class.forName(resultMeta.getColumnClassName(columnNo)))
    )

  def apply(typing: (String, TypingResult)): ColumnDefinition =
    ColumnDefinition(
      name = typing._1,
      typing = typing._2
    )

}

final case class ColumnDefinition(name: String, typing: TypingResult)
