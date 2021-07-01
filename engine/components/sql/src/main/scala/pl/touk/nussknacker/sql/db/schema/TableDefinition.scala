package pl.touk.nussknacker.sql.db.schema

import pl.touk.nussknacker.engine.api.typed.typing.{TypedClass, TypedObjectTypingResult, TypingResult}

import java.sql.ResultSetMetaData

object TableDefinition {

  def apply(resultMeta: ResultSetMetaData): TableDefinition =
    TableDefinition(
      columnDefs = (1 to resultMeta.getColumnCount).map(ColumnDefinition(_, resultMeta)).toList
    )
}

case class TableDefinition(columnDefs: List[ColumnDefinition]) {
  val rowType: TypedObjectTypingResult = TypedObjectTypingResult(columnDefs.map(col => col.name -> col.typing))

  val resultSetType: TypingResult = TypedClass(classOf[java.util.List[_]], rowType :: Nil)
}
