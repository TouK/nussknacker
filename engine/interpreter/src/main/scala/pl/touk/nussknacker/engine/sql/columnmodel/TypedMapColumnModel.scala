package pl.touk.nussknacker.engine.sql.columnmodel

import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.sql.columnmodel.CreateColumnModel.ClazzToSqlType
import pl.touk.nussknacker.engine.sql.{Column, ColumnModel}

import scala.collection.immutable

private[columnmodel] object TypedMapColumnModel {
  def create(typedMap: TypedObjectTypingResult): ColumnModel = {
    ColumnModel(columns(typedMap).toList)
  }

  private val  columns: TypedObjectTypingResult =>  immutable.Iterable[Column] = typedMap => {
    for {
      (name, typ) <- typedMap.fields
      sqlType <- ClazzToSqlType.convert(name, typ, "TypedMap")
    } yield Column(name, sqlType)
  }
}
