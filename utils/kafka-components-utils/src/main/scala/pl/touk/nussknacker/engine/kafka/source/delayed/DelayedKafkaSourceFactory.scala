package pl.touk.nussknacker.engine.kafka.source.delayed

import pl.touk.nussknacker.engine.api.{NodeId, Params}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.util.TimestampUtils

object DelayedKafkaSourceFactory {

  private final val delayValidators = List(MinimalNumberValidator(0), MaximalNumberValidator(Long.MaxValue))

  final val delayParamName = ParameterName("delayInMillis")

  final val delayParameter: ParameterCreatorWithNoDependency with ParameterExtractor[java.lang.Long] =
    ParameterDeclaration
      .optional[java.lang.Long](delayParamName)
      .withCreator(modify = _.copy(validators = delayValidators))

  final val timestampFieldParamName = ParameterName("timestampField")

  final val fallbackTimestampFieldParameter: ParameterCreatorWithNoDependency with ParameterExtractor[String] =
    ParameterDeclaration
      .optional[String](timestampFieldParamName)
      .withCreator(modify =
        _.copy(editor =
          Some(DualParameterEditor(simpleEditor = StringParameterEditor, defaultMode = DualEditorMode.RAW))
        )
      )

  // TODO this is simple way to provide better UX for timestampField usage. But probably instead of taking this further
  // one should try to allow using spel expression here. As it requires some changes in SourceFunction for Kafka, it must wait
  // until sources will be migrated to non-deprecated Source APi.
  def timestampFieldParameter(
      kafkaRecordValueType: Option[TypingResult]
  ): ParameterCreatorWithNoDependency with ParameterExtractor[String] = {
    val editorOpt = kafkaRecordValueType
      .collect { case TypedObjectTypingResult(fields, _, _) => fields.toList }
      .map(_.collect {
        case (paramName, typing) if TimestampUtils.supportedTimestampTypes.contains(typing) =>
          FixedExpressionValue(s"'${paramName}'", paramName)
      })
      .filter(_.nonEmpty)
      .map(_.sortBy(_.label))
      .map(FixedExpressionValue("", "") :: _)
      .map(FixedValuesParameterEditor.apply)
      .map(DualParameterEditor(_, DualEditorMode.SIMPLE))
      .orElse(Some(DualParameterEditor(simpleEditor = StringParameterEditor, defaultMode = DualEditorMode.RAW)))

    ParameterDeclaration
      .optional[String](timestampFieldParamName)
      .withCreator(modify = _.copy(editor = editorOpt))
  }

  def extractTimestampField(params: Params): Option[String] =
    fallbackTimestampFieldParameter.extractValue(params)

  def extractDelayInMillis(params: Params): Option[java.lang.Long] =
    delayParameter.extractValue(params)

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
                Some(timestampFieldParamName)
              )
            )
          case None =>
            List(
              new CustomNodeError(
                nodeId.id,
                s"Field: '$field' doesn't exist in definition: ${fields.keys.mkString(", ")}.",
                Some(timestampFieldParamName)
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
