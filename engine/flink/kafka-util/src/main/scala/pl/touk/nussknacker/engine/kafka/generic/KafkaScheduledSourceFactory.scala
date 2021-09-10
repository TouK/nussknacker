package pl.touk.nussknacker.engine.kafka.generic

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}

object KafkaScheduledSourceFactory {

  val daysValidators = List(
    NotBlankParameterValidator,
    RegExpParameterValidator("((MON|TUE|WED|THU|FRI|SAT|SUN)(, *|$))+",
      "This field value has to be a comma-separated list of days (MON,TUE,WED,THU,FRI,SAT,SUN)",
      "Please fill field with proper list of days"
    )
  )

  val hourValidators = List(MinimalNumberValidator(0), MaximalNumberValidator(24))

  final val DaysParameterName = "days"

  final val DaysParameter = Parameter(DaysParameterName, Typed[String], daysValidators)

  final val HourFromParameterName = "hourFrom"

  final val HourFromParameter = Parameter(HourFromParameterName, Typed[Int], hourValidators)

  final val HourToParameterName = "hourTo"

  final val HourToParameter = Parameter(HourToParameterName, Typed[Int], hourValidators)

  final val TimestampFieldParamName = "timestampField"

  // TODO: consider changing to lazy parameter and add the same parameter also in "not delayed" kafka sources
  final val TimestampFieldParameter = Parameter.optional(TimestampFieldParamName, Typed[String]).copy(
    editor = Some(DualParameterEditor(simpleEditor = StringParameterEditor, defaultMode = DualEditorMode.RAW))
  )

  def extractDays(params: Map[String, Any]): String =
    params(DaysParameterName).asInstanceOf[String]

  def extractHourFrom(params: Map[String, Any]): Int =
    params(HourFromParameterName).asInstanceOf[Int]

  def extractHourTo(params: Map[String, Any]): Int =
    params(HourToParameterName).asInstanceOf[Int]

  def extractTimestampField(params: Map[String, Any]): String =
    params(TimestampFieldParamName).asInstanceOf[String]

  def validateTimestampField(field: String, typingResult: TypingResult)(implicit nodeId: NodeId): List[ProcessCompilationError] = {
    typingResult match {
      case TypedObjectTypingResult(fields, _, _) => fields.get(field) match {
        case Some(fieldTypingResult) if List(Typed[java.lang.Long], Typed[Long]).contains(fieldTypingResult) => List.empty
        case Some(fieldTypingResult) => List(new CustomNodeError(nodeId.id, s"Field: '$field' has invalid type: ${fieldTypingResult.display}.", Some(TimestampFieldParamName)))
        case None => List(new CustomNodeError(nodeId.id, s"Field: '$field' doesn't exist in definition: ${fields.keys.mkString(",")}.", Some(TimestampFieldParamName)))
      }
      case _ => throw new IllegalArgumentException(s"Not supported delayed source type definition: ${typingResult.getClass.getSimpleName}")
    }
  }

}
