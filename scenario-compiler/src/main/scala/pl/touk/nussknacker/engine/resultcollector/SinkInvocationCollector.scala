package pl.touk.nussknacker.engine.resultcollector

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.process.SinkDisplayableValue

trait SinkInvocationCollector extends Serializable {

  def collect(context: Context, result: Any): Unit

}

object SinkInvocationCollector {

  def normalizeCollectedResult(result: Any): Any = result match {
    case displayable: SinkDisplayableValue =>
      displayable.asDisplayableValue
    case other =>
      other
  }

}
