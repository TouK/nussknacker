package pl.touk.nussknacker.engine.api.definition

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.JsonCodec
import io.circe.generic.extras.ConfiguredJsonCodec
import pl.touk.nussknacker.engine.api.CirceUtil._

import java.time.temporal.ChronoUnit
import scala.util.Try

@ConfiguredJsonCodec sealed trait ParameterEditor

case object SpelParameterEditor extends ParameterEditor

case object BoolParameterEditor extends ParameterEditor

case object DateParameterEditor extends ParameterEditor

case object TimeParameterEditor extends ParameterEditor

case object DateTimeParameterEditor extends ParameterEditor

case object TextareaParameterEditor extends ParameterEditor

case object JsonParameterEditor extends ParameterEditor

case object SqlParameterEditor extends ParameterEditor

case object SpelTemplateParameterEditor extends ParameterEditor

case object TabularTypedDataEditor extends ParameterEditor

@JsonCodec case class DurationParameterEditor(timeRangeComponents: List[ChronoUnit]) extends ParameterEditor

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

@JsonCodec case class PeriodParameterEditor(timeRangeComponents: List[ChronoUnit]) extends ParameterEditor

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
case object CronParameterEditor extends ParameterEditor

@JsonCodec case class FixedValuesParameterEditor(possibleValues: List[FixedExpressionValue]) extends ParameterEditor

@JsonCodec case class FixedValuesWithIconParameterEditor(possibleValues: List[FixedExpressionValueWithIcon])
    extends ParameterEditor

@JsonCodec case class FixedValuesWithRadioParameterEditor(possibleValues: List[FixedExpressionValue])
    extends ParameterEditor

// TODO: currently only supports String/Boolean/Long dictionaries (same set of supported types as AdditionalDataValue)
@JsonCodec case class DictParameterEditor(
    dictId: String // dictId must be present in ExpressionConfigDefinition.dictionaries
) extends ParameterEditor
