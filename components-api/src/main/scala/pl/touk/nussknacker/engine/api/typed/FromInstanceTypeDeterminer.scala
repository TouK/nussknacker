package pl.touk.nussknacker.engine.api.typed

import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.json.encoders.ToJsonEncoder
import pl.touk.nussknacker.engine.api.typed.supertype.CommonSupertypeFinder.Default.superTypeOfTypes
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.typed.typing.Typed.{genericTypeClass, typedListWithElementValues}

import scala.jdk.CollectionConverters._

object FromInstanceTypeDeterminer {

  def fromInstance(obj: Any): TypingResult = {
    obj match {
      case null =>
        TypedNull
      case map: Map[String @unchecked, _] =>
        val fieldTypes = typeMapFields(map)
        Typed.record(fieldTypes, mapBasedRecordUnderlyingType[Map[_, _]](fieldTypes))
      case javaMap: java.util.Map[String @unchecked, _] =>
        val fieldTypes = typeMapFields(javaMap.asScala)
        Typed.record(fieldTypes)
      case list: List[_] =>
        genericTypeClass(classOf[List[_]], List(supertypeOfElementTypes(list)))
      case array: Array[_] =>
        Typed(array.getClass)
      case javaList: java.util.List[_] =>
        typedListWithElementValues(
          supertypeOfElementTypes(javaList.asScala.toList).withoutValue,
          javaList
        )
      case set: java.util.Set[_] =>
        genericTypeClass(classOf[java.util.Set[_]], List(supertypeOfElementTypes(set.asScala.toList)))
      case typeFromInstance: TypedFromInstance => typeFromInstance.typingResult
      case other =>
        Typed(other.getClass) match {
          case typedClass: TypedClass =>
            ToJsonEncoder.default.encode(other) match {
              case Valid(_)   => TypedObjectWithValue(typedClass, other)
              case Invalid(_) => typedClass
            }
          case notTypedClass => notTypedClass
        }
    }
  }

  private def typeMapFields(iterable: Iterable[(String, Any)]) = iterable.map { case (k, v) =>
    k -> fromInstance(v)
  }

  private def supertypeOfElementTypes(list: List[_]): TypingResult = {
    superTypeOfTypes(list.map(fromInstance))
  }

}
