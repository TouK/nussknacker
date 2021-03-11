package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext

trait OutputContextStrategy extends Serializable {

  def transform(accumulatedContext: Option[Context], currentContext: Context): Option[Context]

  //do ew actually need it?
  def definition(validationContext: ValidationContext): ValidationContext = validationContext

  def empty: Context = Context("")

}

object FirstContextStrategy extends OutputContextStrategy {
  override def transform(accumulatedContext: Option[Context], currentContext: Context): Option[Context] =
    accumulatedContext.orElse(Some(currentContext))
}

object LastContextStrategy extends OutputContextStrategy {
  override def transform(accumulatedContext: Option[Context], currentContext: Context): Option[Context] =
    Some(currentContext)
}