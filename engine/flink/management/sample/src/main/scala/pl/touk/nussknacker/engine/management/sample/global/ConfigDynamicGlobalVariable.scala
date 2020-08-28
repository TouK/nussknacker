package pl.touk.nussknacker.engine.management.sample.global

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.typed.DynamicGlobalVariable
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}

/**
  * Returns sample configuration - list of typed maps, based on environment property from process properties.
  */
object ConfigDynamicGlobalVariable extends DynamicGlobalVariable {

  import scala.collection.JavaConverters._

  private val configurations = {
    Map(
      "prod" -> List(Map("a" -> 1, "b" -> "B").asJava, Map("a" -> 2, "b" -> "BB").asJava).asJava
    ).withDefaultValue(
      List(Map[String, Any]("a" -> 1).asJava, Map[String, Any]("a" -> 2).asJava, Map[String, Any]("a" -> 3).asJava).asJava
    )
  }
  override def value(metadata: MetaData): Any = {
    configurations(readProperty(metadata))
  }

  override def returnType(metadata: MetaData): TypingResult = {
    // In real scenario typing info obtained from instance can be insufficient.
    Typed.fromInstance(configurations(readProperty(metadata)))
  }

  override def runtimeClass: Class[_] = classOf[java.util.List[_]]

  private def readProperty(metaData: MetaData): String = {
    metaData.additionalFields.flatMap(_.properties.get("environment")).getOrElse("other")
  }
}
