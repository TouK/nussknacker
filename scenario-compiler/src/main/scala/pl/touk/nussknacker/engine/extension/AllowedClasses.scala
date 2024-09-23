package pl.touk.nussknacker.engine.extension

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet

import scala.util.Try

final case class AllowedClasses(namesWithTyping: Map[String, TypingResult]) {
  def get(className: String): Option[TypingResult] =
    namesWithTyping.get(className)
}

object AllowedClasses {

  def apply(set: ClassDefinitionSet): AllowedClasses =
    new AllowedClasses(
      namesWithTyping = set.classDefinitionsMap
        .map { case (clazz, classDefinition) =>
          clazz.getName -> Try(classDefinition.clazzName).toOption
        }
        .collect { case (className: String, Some(t)) =>
          className -> t
        }
        .filterNot(e => isScalaObject(e._1))
    )

  private def isScalaObject(className: String): Boolean =
    className.contains("$")
}
