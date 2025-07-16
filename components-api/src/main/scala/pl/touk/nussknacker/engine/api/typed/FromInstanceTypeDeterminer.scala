package pl.touk.nussknacker.engine.api.typed

import pl.touk.nussknacker.engine.api.json.encoders.ToJsonEncoder
import pl.touk.nussknacker.engine.api.typed.supertype.CommonSupertypeFinder.Default.superTypeOfTypes
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.typed.typing.Typed.{genericTypeClass, typedListWithElementValues}

import java.nio.charset.Charset
import java.time.{ZoneId, ZoneOffset}
import scala.jdk.CollectionConverters._

object FromInstanceTypeDeterminer extends FromInstanceTypeDeterminer

trait FromInstanceTypeDeterminer {

  protected val highPriorityTypeDeterminer: PartialFunction[Any, TypingResult] = PartialFunction.empty

  def fromInstance(obj: Any): TypingResult = {
    obj match {
      case _ if highPriorityTypeDeterminer.isDefinedAt(obj) => highPriorityTypeDeterminer(obj)
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
            val adjustedType = replaceWithGenericTypeIfNeeded(typedClass)
            if (ToJsonEncoder.default.encode(other).isValid)
              TypedObjectWithValue(adjustedType, other)
            else
              adjustedType
          case notTypedClass => notTypedClass
        }
    }
  }

  // We don't want to present very specific types such as ZoneRegion or sun.nio.cs.UTF_8
  private def replaceWithGenericTypeIfNeeded(typedClass: TypedClass) =
    typedClass.klass match {
      case c if classOf[ZoneId].isAssignableFrom(c) && !classOf[ZoneOffset].isAssignableFrom(c) =>
        Typed.typedClass(classOf[ZoneId])
      case c if classOf[Charset].isAssignableFrom(c) =>
        Typed.typedClass(classOf[Charset])
      case _ =>
        typedClass
    }

  private def typeMapFields(iterable: Iterable[(String, Any)]) = iterable.map { case (k, v) =>
    k -> fromInstance(v)
  }

  private def supertypeOfElementTypes(list: List[_]): TypingResult = {
    superTypeOfTypes(list.map(fromInstance))
  }

}
