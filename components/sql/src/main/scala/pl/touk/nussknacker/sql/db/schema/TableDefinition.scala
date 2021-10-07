package pl.touk.nussknacker.sql.db.schema

import pl.touk.nussknacker.engine.api.typed.TypedObjectDefinition
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult}

import java.sql.ResultSetMetaData

object TableDefinition {

  def apply(resultMeta: ResultSetMetaData): TableDefinition =
    TableDefinition(
      columnDefs = (1 to resultMeta.getColumnCount).map(ColumnDefinition(_, resultMeta)).toList
    )

  def apply(typedObjectDefinition: TypedObjectDefinition): TableDefinition = {
    val columnDefinitions = typedObjectDefinition.fields.zipWithIndex
      .map { case (typing, index) =>
        ColumnDefinition(index + 1, typing)
      }
    TableDefinition(
      columnDefs = columnDefinitions.toList
    )
  }
}

case class TableDefinition(columnDefs: List[ColumnDefinition]) {
  val rowType: TypedObjectTypingResult = TypedObjectTypingResult(columnDefs.map(col => col.name -> col.typing))

  val resultSetType: TypingResult = Typed.typedClass(classOf[java.util.List[_]], rowType :: Nil)
}
