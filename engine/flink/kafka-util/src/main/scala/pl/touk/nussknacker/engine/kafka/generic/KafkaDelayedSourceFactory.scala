package pl.touk.nussknacker.engine.kafka.generic

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.definition.{DualParameterEditor, MaximalNumberValidator, MinimalNumberValidator, Parameter, StringParameterEditor}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.typed.typing.Typed

object KafkaDelayedSourceFactory {

  import scala.collection.JavaConverters._

  val delayValidators = List(MinimalNumberValidator(0), MaximalNumberValidator(Long.MaxValue))

  final val DelayParameterName = "delayInMillis"

  final val DelayParameter = Parameter.optional(DelayParameterName, Typed[java.lang.Long])

  final val TimestampFieldParamName = "timestampField"

  final val TimestampParameter = Parameter.optional(TimestampFieldParamName, Typed[String]).copy(
    editor = Some(DualParameterEditor(simpleEditor = StringParameterEditor, defaultMode = DualEditorMode.RAW))
  )

  def extractTimestampField(params: Map[String, Any]): String =
    params(TimestampFieldParamName).asInstanceOf[String]

  def extractDelayInMillis(params: Map[String, Any]): Long =
    params(DelayParameterName).asInstanceOf[Long]

  def validateDelay(value: java.lang.Long)(implicit nodeId: NodeId): List[ProcessCompilationError] = {
    delayValidators.flatMap(_.isValid(DelayParameterName, value.toString, None).swap.toList)
  }

  def validateTimestampField(field: String, definition: java.util.Map[String, _])(implicit nodeId: NodeId): List[ProcessCompilationError] = {
    if (!definition.containsKey(field)) {
      List(new CustomNodeError(nodeId.id, s"Field: '$field' doesn't exist in definition: ${definition.asScala.keys.mkString(",")}.", Some(TimestampFieldParamName)))
    } else {
      List.empty
    }
  }

}
