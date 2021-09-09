package pl.touk.nussknacker.engine.api.definition


import java.time.temporal.ChronoUnit

import io.circe.generic.extras.ConfiguredJsonCodec
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.CirceUtil._
import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import io.circe.Json
import org.apache.commons.lang3.StringUtils

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
  - add CronDefinitionBuilder to additional classes in ExpressionConfig
 */
case object CronParameterEditor extends SimpleParameterEditor

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
