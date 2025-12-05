package pl.touk.nussknacker.engine.api.definition

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.JsonCodec
import io.circe.generic.extras.ConfiguredJsonCodec
import pl.touk.nussknacker.engine.api.CirceUtil._

import java.time.temporal.ChronoUnit
import scala.util.Try

@ConfiguredJsonCodec sealed trait ParameterEditor

// Editor with static values (without SpEL)
@ConfiguredJsonCodec sealed trait StaticParameterEditor

case object SpelParameterEditor extends ParameterEditor

case object StaticStringParameterEditor extends StaticParameterEditor

case object BoolParameterEditor extends ParameterEditor with StaticParameterEditor

case object DateParameterEditor extends ParameterEditor with StaticParameterEditor

case object TimeParameterEditor extends ParameterEditor with StaticParameterEditor

case object DateTimeParameterEditor extends ParameterEditor with StaticParameterEditor

case object TextareaParameterEditor extends ParameterEditor with StaticParameterEditor

case object JsonParameterEditor extends ParameterEditor with StaticParameterEditor

case object JsonTemplateParameterEditor extends ParameterEditor with StaticParameterEditor

case object SqlParameterEditor extends ParameterEditor

case object SpelTemplateParameterEditor extends ParameterEditor

case object TabularTypedDataEditor extends ParameterEditor

@JsonCodec case class DurationParameterEditor(timeRangeComponents: List[ChronoUnit])
    extends ParameterEditor
    with StaticParameterEditor

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

@JsonCodec case class PeriodParameterEditor(timeRangeComponents: List[ChronoUnit])
    extends ParameterEditor
    with StaticParameterEditor

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
case object CronParameterEditor extends ParameterEditor with StaticParameterEditor

@JsonCodec case class FixedValuesParameterEditor(possibleValues: List[FixedExpressionValue])
    extends ParameterEditor
    with StaticParameterEditor

@JsonCodec case class FixedValuesWithIconParameterEditor(possibleValues: List[FixedExpressionValueWithIcon])
    extends ParameterEditor
    with StaticParameterEditor

@JsonCodec case class FixedValuesWithRadioParameterEditor(possibleValues: List[FixedExpressionValue])
    extends ParameterEditor
    with StaticParameterEditor

// TODO: currently only supports String/Boolean/Long dictionaries (same set of supported types as AdditionalDataValue)
@JsonCodec case class DictParameterEditor(
    dictId: String // dictId must be present in ExpressionConfigDefinition.dictionaries
) extends ParameterEditor
    with StaticParameterEditor

@JsonCodec case class MultiSelectEditor(possibleValues: List[MultiSelectFixedValue])
    extends ParameterEditor
    with StaticParameterEditor

case object SpelExpressionsListEditor extends ParameterEditor
