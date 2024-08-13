package pl.touk.nussknacker.engine.util.typing

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.ThreadUtils

import java.util
import scala.jdk.CollectionConverters._

object TypingUtils {

  def typeMapDefinition(definition: java.util.Map[String, _]): TypingResult = {
    typeMapDefinition(definition.asScala.toMap)
  }

  def typeMapDefinition(definition: Map[String, _]): TypingResult = {
    Typed.record(definition.mapValuesNow(typedMapDefinitionFromParameters))
  }

  private def typedMapDefinitionFromParameters(definition: Any): TypingResult = definition match {
    case t: TypingResult =>
      t
    case clazz: Class[_] =>
      Typed(clazz)
    case a: String =>
      loadClassFromName(a)
    case a: Map[String @unchecked, _] =>
      typeMapDefinition(a)
    case a: java.util.Map[String @unchecked, _] =>
      typeMapDefinition(a)
    case list: Seq[_] if list.nonEmpty =>
      typeListDefinition(list)
    case list: util.List[_] if !list.isEmpty =>
      typeListDefinition(list.asScala.toSeq)
    case a =>
      throw new IllegalArgumentException(
        s"Type definition currently supports only class names, nested maps or lists, got $a instead"
      )
  }

  private def typeListDefinition(list: Seq[_]): TypingResult = {
    // TODO: how to handle list definitions better?
    val mapTypingResult = typedMapDefinitionFromParameters(list.head)
    Typed.genericTypeClass[java.util.List[_]](List(mapTypingResult))
  }

  // TODO: how to handle classloaders??
  def loadClassFromName(name: String): TypingResult = {
    val nameIntFixed = if (name == "Int") "Integer" else name // scala.Int to java.lang.Integer
    val langAppended = if (!nameIntFixed.contains(".")) "java.lang." + nameIntFixed else nameIntFixed
    Typed(ThreadUtils.loadUsingContextLoader(langAppended))
  }

}
