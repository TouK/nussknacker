package pl.touk.nussknacker.engine.api.definition

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import io.circe.generic.JsonCodec
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.{Decoder, Encoder, Json}
import org.apache.commons.lang3.StringUtils
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.context.{PartSubGraphCompilationError, ProcessCompilationError}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{EmptyMandatoryParameter, NodeId}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.CirceUtil._

import scala.reflect.ClassTag
import scala.util.Try

sealed trait NodeDependency

case class TypedNodeDependency(clazz: Class[_]) extends NodeDependency

case object OutputVariableNameDependency extends NodeDependency

object Parameter {

  def apply[T: ClassTag](name: String): Parameter = Parameter(name, Typed[T], implicitly[ClassTag[T]].runtimeClass)

  def apply(name: String, typ: TypingResult, runtimeClass: Class[_]): Parameter =
    Parameter(name, typ, runtimeClass, editor = None, validators = List(MandatoryValueValidator), // we want to have mandatory parameters by default because it can protect us from NPE in some cases
      additionalVariables = Map.empty, branchParam = false)

  def optional[T:ClassTag](name: String): Parameter =
    Parameter.optional(name, Typed[T], implicitly[ClassTag[T]].runtimeClass)

  def optional(name: String, typ: TypingResult, runtimeClass: Class[_]): Parameter =
    Parameter(name, typ, runtimeClass, editor = None, validators = List.empty, additionalVariables = Map.empty, branchParam = false)
}

case class Parameter(name: String,
                     typ: TypingResult,
                     runtimeClass: Class[_],
                     editor: Option[ParameterEditor],
                     validators: List[ParameterValidator],
                     additionalVariables: Map[String, TypingResult],
                     branchParam: Boolean) extends NodeDependency {

  def isLazyParameter: Boolean = classOf[LazyParameter[_]].isAssignableFrom(runtimeClass)

  def isOptional: Boolean = !validators.contains(MandatoryValueValidator)

}

@ConfiguredJsonCodec sealed trait ParameterEditor

case object RawParameterEditor extends ParameterEditor

@ConfiguredJsonCodec sealed trait SimpleParameterEditor extends ParameterEditor

case object BoolParameterEditor extends SimpleParameterEditor

case object StringParameterEditor extends SimpleParameterEditor

case object DateParameterEditor extends SimpleParameterEditor

case object TimeParameterEditor extends SimpleParameterEditor

case object DateTimeParameterEditor extends SimpleParameterEditor

@JsonCodec case class FixedValuesParameterEditor(possibleValues: List[FixedExpressionValue]) extends SimpleParameterEditor

@JsonCodec case class FixedExpressionValue(expression: String, label: String)

@JsonCodec case class DualParameterEditor(simpleEditor: SimpleParameterEditor, defaultMode: DualEditorMode) extends ParameterEditor

object DualParameterEditor {
  implicit val dualEditorModeEncoder: Encoder[DualEditorMode] = {
    new Encoder[DualEditorMode] {
      override def apply(editorMode: DualEditorMode): Json = Encoder.encodeString(editorMode.name())
    }
  }

  implicit val decodeDualEditorMode: Decoder[DualEditorMode] = {
    Decoder.decodeString.emapTry(name => Try(DualEditorMode.fromName(name)))
  }
}

/**
 * Extend this trait to configure new parameter validator which should be handled on FE.
 * Please remember that you have to also add your own `pl.touk.nussknacker.engine.definition.validator.ValidatorExtractor`
 * to `pl.touk.nussknacker.engine.definition.validator.ValidatorsExtractor` which should decide whether new validator
 * should appear in configuration for certain parameter
 */
@ConfiguredJsonCodec sealed trait ParameterValidator {

  def isValid(paramName: String, expression: String)(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit]

}

case object MandatoryValueValidator extends ParameterValidator {

  override def isValid(paramName: String, expression: String)(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
    if (StringUtils.isNotBlank(expression)) valid(Unit) else invalid(EmptyMandatoryParameter(paramName))
}
