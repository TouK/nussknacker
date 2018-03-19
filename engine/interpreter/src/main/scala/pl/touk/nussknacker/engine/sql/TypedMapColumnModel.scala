package pl.touk.nussknacker.engine.sql

import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedMapTypingResult, TypingResult}
import pl.touk.nussknacker.engine.sql.CreateColumnModel.MapSqlType

import scala.collection.immutable

object TypedMapColumnModel {
  def unapply(typedMap: TypedMapTypingResult): Some[ColumnModel] = {
    Some(ColumnModel(columns(typedMap).toList))
  }

  private val  columns: TypedMapTypingResult =>  immutable.Iterable[Column] = typedMap => {
    val toClasRef: TypingResult => Option[ClazzRef] = {
      case Typed(possibleTypes) =>
        possibleTypes.headOption.map { typedClass =>
          ClazzRef(typedClass.klass)
        }
      case _ => None
    }
    for {
      (name, typ) <- typedMap.fields
      classRef <- toClasRef(typ)
      sqlType <- MapSqlType.unapply(classRef)
    } yield Column(name, sqlType)
  }
}
