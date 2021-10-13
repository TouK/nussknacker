package pl.touk.nussknacker.engine.api.process

import pl.touk.nussknacker.engine.api.MethodToInvoke
import pl.touk.nussknacker.engine.api.component.Component


trait Sink

/**
  * [[pl.touk.nussknacker.engine.api.process.SinkFactory]] has to have method annotated with [[pl.touk.nussknacker.engine.api.MethodToInvoke]]
  * that returns [[pl.touk.nussknacker.engine.api.process.Sink]]
  *
  * IMPORTANT lifecycle notice:
  * Implementations of this class *must not* allocate resources (connections, file handles etc.)
  */
trait SinkFactory extends Serializable with Component

object SinkFactory {

  def noParam(sink: Sink): SinkFactory =
    new NoParamSinkFactory(sink)

  class NoParamSinkFactory(sink: Sink) extends SinkFactory {
    @MethodToInvoke
    def create(): Sink = sink
  }

}