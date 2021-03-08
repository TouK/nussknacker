package pl.touk.nussknacker.extensions.db

import pl.touk.nussknacker.engine.api.typed.typing.{TypedClass, TypedObjectTypingResult, TypingResult}

import java.sql.ResultSetMetaData

object TableDef {

  def apply(resultMeta: ResultSetMetaData): TableDef =
    TableDef(
      columnDefs = (1 to resultMeta.getColumnCount).map(ColumnDef(_, resultMeta)).toList)
}

case class TableDef(columnDefs: List[ColumnDef]) {
  val rowType: TypedObjectTypingResult = TypedObjectTypingResult(columnDefs.map(col => col.name -> col.typ).toMap)

  val resultSetType: TypingResult = TypedClass(classOf[java.util.List[_]], rowType :: Nil)
}