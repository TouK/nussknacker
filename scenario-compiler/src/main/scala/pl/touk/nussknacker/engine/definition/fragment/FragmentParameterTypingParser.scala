package pl.touk.nussknacker.engine.definition.fragment

import fastparse._
import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet

import scala.util.Try

import SingleLineWhitespace._

class FragmentParameterTypingParser(classLoader: ClassLoader, classDefinitions: ClassDefinitionSet) {

  private def identifier[_: P]: P[String] =
    P(CharIn("a-zA-Z_") ~ CharsWhileIn("a-zA-Z0-9_.", 0)).!

  private def typParser[_: P]: P[TypingResult] =
    P(mapType | listType | setType | simpleType)

  private def simpleType[_: P]: P[TypingResult] =
    identifier.map(resolveInnerClass)

  private def mapType[_: P]: P[TypingResult] =
    P("Map[" ~/ typParser ~ "," ~ typParser ~ "]")
      .map { case (tr1, tr2) => Typed.genericTypeClass[java.util.Map[_, _]](List(tr1, tr2)) }

  private def listType[_: P]: P[TypingResult] =
    P("List[" ~/ typParser ~ "]")
      .map(tr => Typed.genericTypeClass[java.util.List[_]](List(tr)))

  private def setType[_: P]: P[TypingResult] =
    P("Set[" ~/ typParser ~ "]")
      .map(tr => Typed.genericTypeClass[java.util.Set[_]](List(tr)))

  private val classDefinitionsByName = classDefinitions.byName

  private def resolveInnerClass(simpleClassName: String): TypingResult =
    classDefinitionsByName.get(simpleClassName) match {
      case Some(resolvedClassDefinition) =>
        resolvedClassDefinition.clazzName
      case None =>
        // This is fallback - it may be removed and `ClassNotFound` exception may be thrown here after cleaning up the mess with `FragmentClazzRef` class
        Typed(ClassUtils.getClass(classLoader, simpleClassName))
    }

  def parseClassNameToTypingResult(className: String): Try[TypingResult] = {

    Try(parse(className, implicit ctx => typParser(ctx) ~ End) match {
      case Parsed.Success(typingResult, _) => typingResult
      case Parsed.Failure(label, index, extra) =>
        throw new IllegalArgumentException(s"Parsing failed at $index: $label\n${extra.trace().longMsg}")
    })
  }

}
