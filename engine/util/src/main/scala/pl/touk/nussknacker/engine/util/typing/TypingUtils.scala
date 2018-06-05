package pl.touk.nussknacker.engine.util.typing

import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedMapTypingResult, TypingResult}

import scala.collection.JavaConversions._


object TypingUtils {

  def typedMapDefinitionFromParameters(definition: Any): TypingResult = definition match {
    case a: String =>
      loadClassFromName(a)
    case a: java.util.Map[String@unchecked, Any@unchecked] =>
      typeMapDefinition(a)
    //TODO: how to handle list definitions better?
    case list: java.util.ArrayList[_] if !list.isEmpty =>
      val mapTypingResult = typedMapDefinitionFromParameters(list.head)
      new Typed(Set(TypedClass(classOf[List[_]], List(mapTypingResult))))
    case a =>
      throw new IllegalArgumentException(s"Type definition currently supports only class names, nested maps or lists, got $a instead")
  }

  private def typeMapDefinition(definition: java.util.Map[String, Any]): TypingResult = {
    //we force use of Map and not some implicit variants (MapLike) to avoid serialization problems...
    TypedMapTypingResult(Map(definition.toMap.mapValues(typedMapDefinitionFromParameters).toList: _*))
  }

  //TODO: how to handle classloaders??
  private def loadClassFromName(name: String): TypingResult = {
    val langAppended = if (!name.contains(".")) "java.lang." + name else name
    Typed(ClazzRef(ClassUtils.getClass(langAppended)))
  }

}
