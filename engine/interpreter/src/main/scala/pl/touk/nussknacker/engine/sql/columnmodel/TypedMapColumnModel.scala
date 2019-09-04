package pl.touk.nussknacker.engine.sql.columnmodel

import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypedUnion, TypingResult, Unknown}
import pl.touk.nussknacker.engine.sql.columnmodel.CreateColumnModel.ClazzToSqlType
import pl.touk.nussknacker.engine.sql.{Column, ColumnModel}

import scala.collection.immutable

private[columnmodel] object TypedMapColumnModel {
  def create(typedMap: TypedObjectTypingResult): ColumnModel = {
    ColumnModel(columns(typedMap).toList)
  }

  private val  columns: TypedObjectTypingResult =>  immutable.Iterable[Column] = typedMap => {
    val toClasRef: TypingResult => Option[ClazzRef] = {
      case typedClass: TypedClass =>
        Some(ClazzRef(typedClass.klass))
      case _: TypedObjectTypingResult | _: TypedUnion | Unknown =>
        None
    }
    for {
      (name, typ) <- typedMap.fields
      classRef <- toClasRef(typ)
      sqlType <- ClazzToSqlType.convert(name, classRef, "TypedMap")
    } yield Column(name, sqlType)
  }
}
