package pl.touk.nussknacker.engine.api.definition

import io.circe.generic.JsonCodec
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.{Decoder, Encoder, Json}
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.CirceUtil._

import scala.reflect.ClassTag
import scala.util.Try

sealed trait NodeDependency

case class TypedNodeDependency(clazz: Class[_]) extends NodeDependency

case object OutputVariableNameDependency extends NodeDependency

object Parameter {
  def apply[T:ClassTag](name: String): Parameter = Parameter(name, Typed[T], implicitly[ClassTag[T]].runtimeClass)
}

case class Parameter(name: String,
                     typ: TypingResult,
                     runtimeClass: Class[_],
                     editor: Option[ParameterEditor] = None,
                     additionalVariables: Map[String, TypingResult] = Map.empty,
                     branchParam: Boolean = false) extends NodeDependency {

  def isLazyParameter: Boolean = classOf[LazyParameter[_]].isAssignableFrom(runtimeClass)

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
