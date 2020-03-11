package pl.touk.nussknacker.engine.api.definition

import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.CirceUtil._

import scala.reflect.ClassTag

sealed trait NodeDependency

case class TypedNodeDependency(clazz: Class[_]) extends NodeDependency

case object OutputVariableNameDependency extends NodeDependency

object Parameter {

  def apply[T: ClassTag](name: String): Parameter = Parameter(name, Typed[T], implicitly[ClassTag[T]].runtimeClass)

  def apply(name: String, typ: TypingResult, runtimeClass: Class[_], validators: List[ParameterValidator] = List.empty): Parameter =
    Parameter(name, typ, runtimeClass, editor = None, validators = List(MandatoryValueValidator) ++ validators, // we want to have mandatory parameters by default because it can protect us from NPE in some cases
      additionalVariables = Map.empty, branchParam = false)

  def optional[T:ClassTag](name: String): Parameter =
    Parameter.optional(name, Typed[T], implicitly[ClassTag[T]].runtimeClass)

  def optional(name: String, typ: TypingResult, runtimeClass: Class[_]): Parameter =
    Parameter(name, typ, runtimeClass, editor = None, validators = List.empty, additionalVariables = Map.empty, branchParam = false)

  def notBlank(name: String, typ: TypingResult, runtimeClass: Class[_]): Parameter =
    Parameter(name, typ, runtimeClass, validators = List(NotBlankValueValidator))
}

case class Parameter(name: String,
                     typ: TypingResult,
                     runtimeClass: Class[_],
                     editor: Option[ParameterEditor],
                     validators: List[ParameterValidator],
                     additionalVariables: Map[String, TypingResult],
                     branchParam: Boolean) extends NodeDependency {

  def isLazyParameter: Boolean = classOf[LazyParameter[_]].isAssignableFrom(runtimeClass)
}
