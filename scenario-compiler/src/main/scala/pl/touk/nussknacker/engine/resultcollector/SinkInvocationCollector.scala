package pl.touk.nussknacker.engine.resultcollector

import pl.touk.nussknacker.engine.api.Context

trait SinkInvocationCollector extends Serializable {

  def collect(context: Context, result: Any): Unit

}
