package pl.touk.nussknacker.engine.kafka.source.delayed

import pl.touk.nussknacker.engine.api.{NodeId, Params}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.util.TimestampUtils

object DelayedKafkaSourceFactory {

  private final val delayValidators = List(MinimalNumberValidator(0), MaximalNumberValidator(Long.MaxValue))

  final val DelayParameterName = "delayInMillis"

  final val DelayParameter =
    Parameter.optional(DelayParameterName, Typed[java.lang.Long]).copy(validators = delayValidators)

  final val TimestampFieldParamName = "timestampField"

  // TODO: consider changing to lazy parameter and add the same parameter also in "not delayed" kafka sources
  final val TimestampFieldParameter = Parameter
    .optional(TimestampFieldParamName, Typed[String])
    .copy(
      editor = Some(DualParameterEditor(simpleEditor = StringParameterEditor, defaultMode = DualEditorMode.RAW))
    )

  def extractTimestampField(params: Params): String = params.extractUnsafe(TimestampFieldParamName)

  def extractDelayInMillis(params: Params): Long = params.extractUnsafe(DelayParameterName)

  def validateTimestampField(field: String, typingResult: TypingResult)(
      implicit nodeId: NodeId
  ): List[ProcessCompilationError] = {
    typingResult match {
      case TypedObjectTypingResult(fields, _, _) =>
        fields.get(field) match {
          case Some(fieldTypingResult) if TimestampUtils.supportedTimestampTypes.contains(fieldTypingResult) =>
            List.empty
          case Some(fieldTypingResult) =>
            List(
              new CustomNodeError(
                nodeId.id,
                s"Field: '$field' has invalid type: ${fieldTypingResult.display}.",
                Some(TimestampFieldParamName)
              )
            )
          case None =>
            List(
              new CustomNodeError(
                nodeId.id,
                s"Field: '$field' doesn't exist in definition: ${fields.keys.mkString(", ")}.",
                Some(TimestampFieldParamName)
              )
            )
        }
      case _ =>
        throw new IllegalArgumentException(
          s"Not supported delayed source type definition: ${typingResult.getClass.getSimpleName}"
        )
    }
  }

}
