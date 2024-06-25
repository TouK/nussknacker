package pl.touk.nussknacker.sql.db.schema

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}

import java.sql.ResultSetMetaData

object TableDefinition {

  def apply(resultMeta: ResultSetMetaData): TableDefinition =
    TableDefinition(
      columnDefs = (1 to resultMeta.getColumnCount).map(ColumnDefinition(_, resultMeta)).toList
    )

  def apply2(fields: List[(String, TypingResult)]): TableDefinition = {
    val columnDefinitions = fields
      .map { typing =>
        ColumnDefinition(typing)
      }
    TableDefinition(
      columnDefs = columnDefinitions
    )
  }

}

final case class TableDefinition(columnDefs: List[ColumnDefinition]) {
  val rowType: TypedObjectTypingResult = Typed.record(columnDefs.map(col => col.name -> col.typing).toMap)

  val resultSetType: TypingResult = Typed.genericTypeClass(classOf[java.util.List[_]], rowType :: Nil)
}
