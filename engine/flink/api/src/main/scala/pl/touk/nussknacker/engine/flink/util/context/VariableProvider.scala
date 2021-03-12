package pl.touk.nussknacker.engine.flink.util.context

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext

import scala.reflect.ClassTag

abstract class VariableProvider[T: ClassTag] {
  def validationContext(context: ValidationContext, name: String)(implicit nodeId: NodeId): ValidationContext
  def initContext(processId: String, taskName: String): InitContextFunction[T]
}
