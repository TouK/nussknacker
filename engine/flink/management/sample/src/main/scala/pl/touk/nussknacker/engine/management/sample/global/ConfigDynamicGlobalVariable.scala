package pl.touk.nussknacker.engine.management.sample.global

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.typed.typing.TypedClass
import pl.touk.nussknacker.engine.api.typed.{DynamicGlobalVariable, typing}
import pl.touk.nussknacker.engine.util.typing.TypingUtils

/**
  * Returns sample configuration - list of typed maps, based on environment property from process properties.
  */
object ConfigDynamicGlobalVariable extends DynamicGlobalVariable {

  import scala.collection.JavaConverters._

  case class Configuration(value: java.util.List[java.util.Map[String, Any]], typingResult: typing.TypingResult)

  private val configurations = Map(
    "prod" -> Configuration(
      value = List(Map("a" -> 1, "b" -> "B").asJava, Map("a" -> 2, "b" -> "BB").asJava).asJava,
      typingResult = TypedClass(classOf[java.util.List[_]], List(TypingUtils.typeMapDefinition(Map("a" -> "Integer", "b" -> "String"))))
    )
  ).withDefaultValue(
    Configuration(
      value = List(Map[String, Any]("a" -> 1).asJava, Map[String, Any]("a" -> 2).asJava, Map[String, Any]("a" -> 3).asJava).asJava,
      typingResult = TypedClass(classOf[java.util.List[_]], List(TypingUtils.typeMapDefinition(Map("a" -> "Integer"))))
    )
  )

  override def value(metadata: MetaData): Any = {
    configurations(readProperty(metadata)).value
  }

  override def returnType(metadata: MetaData): typing.TypingResult = {
    configurations(readProperty(metadata)).typingResult
  }

  override def runtimeClass: Class[_] = classOf[java.util.List[_]]

  private def readProperty(metaData: MetaData): String = {
    metaData.additionalFields.flatMap(_.properties.get("environment")).getOrElse("other")
  }
}
