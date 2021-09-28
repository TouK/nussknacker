package pl.touk.nussknacker.engine.api.definition

import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, OutputVariableNameValue, TypedNodeDependencyValue}
import pl.touk.nussknacker.engine.api.typed.MissingOutputVariableException
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.util.NotNothing

import scala.reflect.runtime.universe._

sealed trait NodeDependency

/**
 * This trait reduce boilerplate defining `GenericNodeTransformation` and reduce risk that definition of node dependencies
 * will desynchronize with implementation code using values
 */
trait ValueExtractor { self: NodeDependency =>
  type RuntimeValue

  def extract(values: List[NodeDependencyValue]): RuntimeValue
}

case class TypedNodeDependency[T](clazz: Class[T]) extends NodeDependency with ValueExtractor {
  override type RuntimeValue = T

  override def extract(values: List[NodeDependencyValue]): T = {
    values.collectFirst {
      case out: TypedNodeDependencyValue if clazz.isInstance(out.value) => out.value.asInstanceOf[T]
    }.getOrElse(throw new IllegalStateException(s"Missing node dependency of class: $clazz"))
  }
}

case object OutputVariableNameDependency extends NodeDependency with ValueExtractor {
  override type RuntimeValue = String

  override def extract(values: List[NodeDependencyValue]): String = {
    values.collectFirst {
      case out: OutputVariableNameValue => out.name
    }.getOrElse(throw MissingOutputVariableException)
  }
}

object Parameter {

  def apply[T: TypeTag: NotNothing](name: String): Parameter = Parameter(name, Typed.fromDetailedType[T])

  // we want to have mandatory parameters by default because it can protect us from NPE in some cases)
  def apply(name: String, typ: TypingResult): Parameter =
    Parameter(name, typ, validators = List(MandatoryParameterValidator))

  def apply(name: String, typ: TypingResult, validators: List[ParameterValidator]): Parameter =
    Parameter(name, typ, editor = None, validators = validators, defaultValue = None, additionalVariables = Map.empty, variablesToHide = Set.empty,
      branchParam = false, isLazyParameter = false, scalaOptionParameter = false, javaOptionalParameter = false)

  def optional[T: TypeTag: NotNothing](name: String): Parameter =
    Parameter.optional(name, Typed.fromDetailedType[T])

  // Represents optional parameter annotated with @Nullable, if you want to emulate scala Option or java Optional,
  // you should redefine scalaOptionParameter and javaOptionalParameter
  def optional(name: String, typ: TypingResult): Parameter =
    Parameter(name, typ, editor = None, validators = List.empty, defaultValue = None, additionalVariables = Map.empty, variablesToHide = Set.empty,
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
                     defaultValue: Option[String],
                     additionalVariables: Map[String, TypingResult],
                     variablesToHide: Set[String],
                     branchParam: Boolean,
                     isLazyParameter: Boolean,
                     scalaOptionParameter: Boolean,
                     javaOptionalParameter: Boolean) extends NodeDependency
