package pl.touk.nussknacker.engine.api.definition

import io.circe.generic.JsonCodec
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Decoder, Encoder, HCursor, Json}
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.editor.FixedValuesEditorMode
import pl.touk.nussknacker.engine.api.editor.DualEditorMode

import java.time.temporal.ChronoUnit
import scala.util.Try

@ConfiguredJsonCodec sealed trait ParameterEditor

case object RawParameterEditor extends ParameterEditor

@ConfiguredJsonCodec sealed trait SimpleParameterEditor extends ParameterEditor

case object BoolParameterEditor extends SimpleParameterEditor

case object StringParameterEditor extends SimpleParameterEditor

case object DateParameterEditor extends SimpleParameterEditor

case object TimeParameterEditor extends SimpleParameterEditor

case object DateTimeParameterEditor extends SimpleParameterEditor

case object TextareaParameterEditor extends SimpleParameterEditor

case object JsonParameterEditor extends SimpleParameterEditor

case object SqlParameterEditor extends SimpleParameterEditor

case object SpelTemplateParameterEditor extends SimpleParameterEditor

case object TabularTypedDataEditor extends SimpleParameterEditor

@JsonCodec case class DurationParameterEditor(timeRangeComponents: List[ChronoUnit]) extends SimpleParameterEditor

object DurationParameterEditor {

  implicit val chronoUnitEncoder: Encoder[ChronoUnit] = {
    new Encoder[ChronoUnit] {
      override def apply(chronoUnit: ChronoUnit): Json = Encoder.encodeString(chronoUnit.name())
    }
  }

  implicit val chronoUnitDecoder: Decoder[ChronoUnit] = {
    Decoder.decodeString.emapTry(name => Try(ChronoUnit.valueOf(name)))
  }

}

@JsonCodec case class PeriodParameterEditor(timeRangeComponents: List[ChronoUnit]) extends SimpleParameterEditor

object PeriodParameterEditor {

  implicit val chronoUnitEncoder: Encoder[ChronoUnit] = {
    new Encoder[ChronoUnit] {
      override def apply(chronoUnit: ChronoUnit): Json = Encoder.encodeString(chronoUnit.name())
    }
  }

  implicit val chronoUnitDecoder: Decoder[ChronoUnit] = {
    Decoder.decodeString.emapTry(name => Try(ChronoUnit.valueOf(name)))
  }

}

/* To use this editor you have to:
  - add https://github.com/jmrozanec/cron-utils to model classpath
  - add CronDefinitionBuilder, CronParser and CronType to additional classes in ExpressionConfig
 */
case object CronParameterEditor extends SimpleParameterEditor

case class FixedValuesParameterEditor(
    possibleValues: List[FixedExpressionValue],
    mode: FixedValuesEditorMode = FixedValuesEditorMode.LIST
) extends SimpleParameterEditor

@JsonCodec case class FixedValuesWithIconParameterEditor(possibleValues: List[FixedExpressionValueWithIcon])
    extends SimpleParameterEditor

// TODO: currently only supports String/Boolean/Long dictionaries (same set of supported types as AdditionalDataValue)
@JsonCodec case class DictParameterEditor(
    dictId: String // dictId must be present in ExpressionConfigDefinition.dictionaries
) extends SimpleParameterEditor

@JsonCodec case class DualParameterEditor(simpleEditor: SimpleParameterEditor, defaultMode: DualEditorMode)
    extends ParameterEditor

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

object FixedValuesParameterEditor {
  def apply(possibleValues: List[FixedExpressionValue]): FixedValuesParameterEditor =
    FixedValuesParameterEditor(possibleValues, mode = FixedValuesEditorMode.LIST)

  implicit val fixedValuesEditorModeEncoder: Encoder[FixedValuesEditorMode] = new Encoder[FixedValuesEditorMode] {
    override def apply(a: FixedValuesEditorMode): Json = Encoder.encodeString(a.name())
  }

  implicit val fixedValuesEditorModeDecoder: Decoder[FixedValuesEditorMode] =
    Decoder.decodeString.emapTry(name => Try(FixedValuesEditorMode.fromName(name)))

  implicit val fixedValuesParameterEditorEncoder: Encoder[FixedValuesParameterEditor] =
    deriveEncoder[FixedValuesParameterEditor]

  implicit val fixedValuesParameterEditorDecoder: Decoder[FixedValuesParameterEditor] =
    Decoder
      .forProduct2("possibleValues", "mode")(FixedValuesParameterEditor.apply)
      .or(
        Decoder.forProduct1("possibleValues")((p: List[FixedExpressionValue]) =>
          FixedValuesParameterEditor(p, FixedValuesEditorMode.LIST)
        )
      )

}
