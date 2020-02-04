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
    val toTypedClass: TypingResult => Option[TypedClass] = {
      case typedClass: TypedClass =>
        Some(typedClass)
      case _: TypedObjectTypingResult | _: TypedUnion | Unknown | _:TypedDict | _:TypedTaggedValue =>
        None
    }
    for {
      (name, typ) <- typedMap.fields
      typedClass <- toTypedClass(typ)
      sqlType <- ClazzToSqlType.convert(name, typedClass, "TypedMap")
    } yield Column(name, sqlType)
  }
}
