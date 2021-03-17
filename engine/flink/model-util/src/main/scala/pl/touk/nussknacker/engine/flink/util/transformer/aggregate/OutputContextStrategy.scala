package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import pl.touk.nussknacker.engine.api.Context

//This trait is used in UnwrappingAggregateFunction to determine which Context will be used in emitted window
//TODO: should we define also ValidationContext transformation here?
trait OutputContextStrategy extends Serializable {

  def transform(accumulatedContext: Option[Context], currentContext: Context): Option[Context]

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