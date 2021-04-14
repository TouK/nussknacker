package pl.touk.nussknacker.engine.management.sample.global

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.typed.{TypedGlobalVariable, TypedMap}

import scala.collection.immutable.ListMap

/**
  * Returns sample configuration - list of typed maps, based on environment property from process properties.
  */
object ConfigTypedGlobalVariable extends TypedGlobalVariable {

  import scala.collection.JavaConverters._

  private val configurations = Map(
      "prod" -> List(TypedMap(ListMap("a" -> 1, "b" -> "B")), TypedMap(ListMap("a" -> 2, "b" -> "BB"))).asJava
    ).withDefaultValue(
      List(TypedMap(ListMap[String, Any]("a" -> 1)), TypedMap(ListMap[String, Any]("a" -> 2)), TypedMap(ListMap[String, Any]("a" -> 3))).asJava
    )

  override def value(metadata: MetaData): Any = {
    configurations(readProperty(metadata))
  }

  override def returnType(metadata: MetaData): TypingResult = {
    // In real scenario typing info obtained from instance can be insufficient.
    Typed.fromInstance(configurations(readProperty(metadata)))
  }

  override def initialReturnType: TypingResult = Typed(classOf[java.util.List[_]])

  private def readProperty(metaData: MetaData): String = {
    metaData.additionalFields.flatMap(_.properties.get("environment")).getOrElse("other")
  }
}
