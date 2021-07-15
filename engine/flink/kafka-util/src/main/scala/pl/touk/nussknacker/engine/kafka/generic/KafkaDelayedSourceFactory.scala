package pl.touk.nussknacker.engine.kafka.generic

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.definition.{DualParameterEditor, MaximalNumberValidator, MinimalNumberValidator, Parameter, StringParameterEditor}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}

object KafkaDelayedSourceFactory {

  import scala.collection.JavaConverters._

  val delayValidators = List(MinimalNumberValidator(0), MaximalNumberValidator(Long.MaxValue))

  final val DelayParameterName = "delayInMillis"

  final val DelayParameter = Parameter.optional(DelayParameterName, Typed[java.lang.Long])

  final val TimestampFieldParamName = "timestampField"

  // TODO: consider changing to lazy parameter and add the same parameter also in "not delayed" kafka sources
  final val TimestampFieldParameter = Parameter.optional(TimestampFieldParamName, Typed[String]).copy(
    editor = Some(DualParameterEditor(simpleEditor = StringParameterEditor, defaultMode = DualEditorMode.RAW))
  )

  def extractTimestampField(params: Map[String, Any]): String =
    params(TimestampFieldParamName).asInstanceOf[String]

  def extractDelayInMillis(params: Map[String, Any]): Long =
    params(DelayParameterName).asInstanceOf[Long]

  def validateDelay(value: java.lang.Long)(implicit nodeId: NodeId): List[ProcessCompilationError] = {
    delayValidators.flatMap(_.isValid(DelayParameterName, value.toString, None).swap.toList)
  }

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
