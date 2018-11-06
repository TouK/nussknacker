package pl.touk.nussknacker.engine.util.typing

import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult}

import scala.collection.JavaConverters._


object TypingUtils {

  def typeMapDefinition(definition: java.util.Map[String, _]): TypingResult = {
    //we force use of Map and not some implicit variants (MapLike) to avoid serialization problems...
    TypedObjectTypingResult(Map(definition.asScala.toMap.mapValues(typedMapDefinitionFromParameters).toList: _*))
  }

  private def typedMapDefinitionFromParameters(definition: Any): TypingResult = definition match {
    case a: String =>
      loadClassFromName(a)
    case a: java.util.Map[String@unchecked, _] =>
      typeMapDefinition(a)
    //TODO: how to handle list definitions better?
    case list: java.util.ArrayList[_] if !list.isEmpty =>
      val mapTypingResult = typedMapDefinitionFromParameters(list.get(0))
      new Typed(Set(TypedClass(classOf[java.util.List[_]], List(mapTypingResult))))
    case a =>
      throw new IllegalArgumentException(s"Type definition currently supports only class names, nested maps or lists, got $a instead")
  }

  //TODO: how to handle classloaders??
  private def loadClassFromName(name: String): TypingResult = {
    val langAppended = if (!name.contains(".")) "java.lang." + name else name
    Typed(ClazzRef(ClassUtils.getClass(langAppended)))
  }

}
