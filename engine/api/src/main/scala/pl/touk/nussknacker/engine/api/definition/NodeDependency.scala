package pl.touk.nussknacker.engine.api.definition

import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.CirceUtil._

import scala.reflect.ClassTag

sealed trait NodeDependency

case class TypedNodeDependency(clazz: Class[_]) extends NodeDependency

case object OutputVariableNameDependency extends NodeDependency

object Parameter {

  def apply[T: ClassTag](name: String): Parameter = Parameter(name, Typed[T])

  // we want to have mandatory parameters by default because it can protect us from NPE in some cases)
  def apply(name: String, typ: TypingResult): Parameter =
    Parameter(name, typ, validators = List(MandatoryParameterValidator))

  def apply(name: String, typ: TypingResult, validators: List[ParameterValidator]): Parameter =
    Parameter(name, typ, editor = None, validators = validators, additionalVariables = Map.empty,
      branchParam = false, isLazyParameter = false, scalaOptionParameter = false, javaOptionalParameter = false)

  @deprecated("Passing runtimeClass to Parameter.apply is deprecated in favor of passing isLazyParameter", "0.1.0")
  def apply(name: String,
            typ: TypingResult,
            runtimeClass: Class[_],
            editor: Option[ParameterEditor],
            validators: List[ParameterValidator],
            additionalVariables: Map[String, TypingResult],
            branchParam: Boolean): Parameter = {
    val isLazyParameter = classOf[LazyParameter[_]].isAssignableFrom(runtimeClass)
    Parameter(name, typ, editor, validators, additionalVariables, branchParam, isLazyParameter,
      scalaOptionParameter = false, javaOptionalParameter = false)
  }

  def optional[T:ClassTag](name: String): Parameter =
    Parameter.optional(name, Typed[T])

  // Represents optional parameter annotated with @Nullable, if you want to emulate scala Option or java Optional,
  // you should redefine scalaOptionParameter and javaOptionalParameter
  def optional(name: String, typ: TypingResult): Parameter =
    Parameter(name, typ, editor = None, validators = List.empty, additionalVariables = Map.empty,
      branchParam = false, isLazyParameter = false, scalaOptionParameter = false, javaOptionalParameter = false)

}

object NotBlankParameter {

  def apply(name: String, typ: TypingResult): Parameter =
    Parameter(name, typ, validators = List(NotBlankParameterValidator))

}

case class Parameter(name: String,
                     typ: TypingResult,
                     editor: Option[ParameterEditor],
                     validators: List[ParameterValidator],
                     additionalVariables: Map[String, TypingResult],
                     branchParam: Boolean,
                     isLazyParameter: Boolean,
                     scalaOptionParameter: Boolean,
                     javaOptionalParameter: Boolean) extends NodeDependency
