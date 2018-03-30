package pl.touk.nussknacker.engine.sql

import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedMapTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.sql.CreateColumnModel.ClazzToSqlType

import scala.collection.immutable

object TypedMapColumnModel {
  def create(typedMap: TypedMapTypingResult): ColumnModel = {
    ColumnModel(columns(typedMap).toList)
  }

  private val  columns: TypedMapTypingResult =>  immutable.Iterable[Column] = typedMap => {
    val toClasRef: TypingResult => Option[ClazzRef] = {
      case Typed(possibleTypes) =>
        possibleTypes.headOption.map { typedClass => ClazzRef(typedClass.klass) }
      case _: TypedMapTypingResult | Unknown =>
        None
    }
    for {
      (name, typ) <- typedMap.fields
      classRef <- toClasRef(typ)
      sqlType <- ClazzToSqlType.convert(name, classRef)
    } yield Column(name, sqlType)
  }
}
