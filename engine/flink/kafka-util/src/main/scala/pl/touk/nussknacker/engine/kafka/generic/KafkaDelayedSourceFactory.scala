package pl.touk.nussknacker.engine.kafka.generic

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.definition.{DualParameterEditor, MandatoryParameterValidator, MaximalNumberValidator, MinimalNumberValidator, NotBlankParameterValidator, Parameter, StringParameterEditor}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode

object KafkaDelayedSourceFactory {

  import scala.collection.JavaConverters._

  val delayValidators = List(MinimalNumberValidator(0), MaximalNumberValidator(Long.MaxValue))

  final val DelayParameterName = "delayInMillis"

  final val DelayParameter = Parameter[java.lang.Long](DelayParameterName).copy(
    validators = List(MandatoryParameterValidator, NotBlankParameterValidator)
  )

  final val TimestampFieldParamName = "timestampField"

  final val TimestampParameter = Parameter[String](TimestampFieldParamName).copy(
    editor = Some(DualParameterEditor(simpleEditor = StringParameterEditor, defaultMode = DualEditorMode.RAW)),
    validators = List(MandatoryParameterValidator, NotBlankParameterValidator)
  )

  def extractTimestampField(params: Map[String, Any]): String =
    params(TimestampFieldParamName).asInstanceOf[String]

  def extractDelayInMillis(params: Map[String, Any]): Long =
    params(DelayParameterName).asInstanceOf[Long]

  def validateDelay(value: java.lang.Long)(implicit nodeId: NodeId): List[ProcessCompilationError] = {
    Option(value).map(v => delayValidators.flatMap(_.isValid(DelayParameterName, v.toString, None).swap.toList)).getOrElse(Nil)
  }

  def validateTimestampField(field: String, definition: java.util.Map[String, _])(implicit nodeId: NodeId): List[ProcessCompilationError] = {
    Option(field).map(f => {
      if (!definition.containsKey(f)) {
        List(new CustomNodeError(nodeId.id, s"Field: '$f' doesn't exist in definition: ${definition.asScala.keys.mkString(",")}.", Some(TimestampFieldParamName)))
      } else {
        List.empty
      }
    }).getOrElse(Nil)
  }

}
