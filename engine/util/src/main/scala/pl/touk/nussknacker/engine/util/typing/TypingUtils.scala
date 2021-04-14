package pl.touk.nussknacker.engine.util.typing

import java.util
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.util.ThreadUtils

import scala.collection.JavaConverters._
import scala.collection.immutable.ListMap
import scala.collection.mutable


object TypingUtils {

  def typeMapDefinition(definition: java.util.LinkedHashMap[String, _]): TypingResult = {
    val arr = new mutable.ArrayBuffer[(String, _)]()
    definition.forEach((k: String, v: Any) => arr.prepend((k, v)))
    typeMapDefinition(ListMap(arr: _*))
  }

  def typeMapDefinition(definition: ListMap[String, _]): TypingResult = {
    //we force use of ListMap and not some implicit variants (MapLike) to avoid serialization problems...
    val fieldsTyping = definition.map { case (fieldName, a) =>
      (fieldName, typedMapDefinitionFromParameters(a))
    }
    TypedObjectTypingResult(fieldsTyping)
  }

  private def typedMapDefinitionFromParameters(definition: Any): TypingResult = definition match {
    case t: TypingResult =>
      t
    case clazz: Class[_] =>
      Typed(clazz)
    case a: String =>
      loadClassFromName(a)
    case a: ListMap[String@unchecked, _] =>
      typeMapDefinition(a)
    case a: java.util.LinkedHashMap[String@unchecked, _] =>
      typeMapDefinition(a)
    case list: Seq[_] if list.nonEmpty =>
      typeListDefinition(list)
    case list: util.List[_] if !list.isEmpty =>
      typeListDefinition(list)
    case a =>
      throw new IllegalArgumentException(s"Type definition currently supports only class names, nested maps or lists, got $a instead")
  }

  private def typeListDefinition(list: util.List[_]): TypingResult = {
    typeListDefinition(list.asScala)
  }

  private def typeListDefinition(list: Seq[_]): TypingResult = {
    //TODO: how to handle list definitions better?
    val mapTypingResult = typedMapDefinitionFromParameters(list.head)
    Typed.genericTypeClass[java.util.List[_]](List(mapTypingResult))
  }

  //TODO: how to handle classloaders??
  def loadClassFromName(name: String): TypingResult = {
    val langAppended = if (!name.contains(".")) "java.lang." + name else name
    Typed(ThreadUtils.loadUsingContextLoader(langAppended))
  }

}
