package pl.touk.nussknacker.engine.spel.typer

import cats.data.NonEmptyList
import org.springframework.expression.spel.ast.MethodReference
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.definition.TypeInfos.{ClazzDefinition, MethodInfo}
import pl.touk.nussknacker.engine.types.EspTypeUtils

object TypeMethodReference {
  def apply(methodReference: MethodReference, currentResults: List[TypingResult]): Either[String, TypingResult] =
    new TypeMethodReference(methodReference, currentResults).call
}

// TODO:
// Currently SpEL methods are naively and optimistically type checked. This is, for overloaded methods with
// different arity, validation is successful when SpEL MethodReference provides the number of parameters greater
// or equal to method with the smallest arity.
class TypeMethodReference(methodReference: MethodReference, currentResults: List[TypingResult]) {
  def call: Either[String, TypingResult] =
    currentResults.headOption match {
      case Some(tc: ScalarTypingResult) =>
        typeFromClazzDefinitions(extractClazzDefinitions(Set(tc)))
      case Some(TypedUnion(nestedTypes)) =>
        typeFromClazzDefinitions(extractClazzDefinitions(nestedTypes))
      case _ =>
        Right(Unknown)
    }

  private lazy val paramsCount = methodReference.getChildCount

  private def extractClazzDefinitions(typedClasses: Set[ScalarTypingResult]): List[ClazzDefinition] =
    typedClasses.map(typedClass =>
      EspTypeUtils.clazzDefinition(typedClass.objType.klass)(ClassExtractionSettings.Default)
    ).toList

  private def typeFromClazzDefinitions(clazzDefinitions: List[ClazzDefinition]): Either[String, TypingResult] =
    clazzDefinitions match {
      case Nil =>
        Right(Unknown)
      case _ =>
        clazzDefinitions.flatMap(_.methods.get(methodReference.getName)) match {
          case Nil => Right(Unknown)
          case methodInfoes => typeFromMethodInfoes(methodInfoes)
        }
    }

  private def typeFromMethodInfoes(methodInfoes: List[MethodInfo]): Either[String, TypingResult] =
    methodInfoes.filter(_.parameters.size <= paramsCount) match {
      case Nil =>
        Left(s"Invalid arity for '${methodReference.getName}'")
      case h::t =>
        val clazzRefs = NonEmptyList(h, t).map(_.refClazz)
        val typingResult = typeFromClazzRefs(clazzRefs)
        Right(typingResult)
    }

  private def typeFromClazzRefs(clazzRefs: NonEmptyList[ClazzRef]): TypingResult =
    Typed(clazzRefs.map(Typed(_)).toList.toSet)

}
