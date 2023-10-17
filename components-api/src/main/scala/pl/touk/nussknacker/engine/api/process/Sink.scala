package pl.touk.nussknacker.engine.api.process

import pl.touk.nussknacker.engine.api.MethodToInvoke
import pl.touk.nussknacker.engine.api.component.{AllProcessingModesComponent, Component}

trait Sink

/**
  * [[pl.touk.nussknacker.engine.api.process.SinkFactory]] has to have method annotated with [[pl.touk.nussknacker.engine.api.MethodToInvoke]]
  * that returns [[pl.touk.nussknacker.engine.api.process.Sink]]
  *
  * IMPORTANT lifecycle notice:
  * Implementations of this class *must not* allocate resources (connections, file handles etc.)
  *
  * To make implementation easier, by default, sink factory handle all processing modes. If you have some
  * processing mode specific component, you should override allowedProcessingModes method
  */
trait SinkFactory extends Serializable with Component with AllProcessingModesComponent

object SinkFactory {

  def noParam(sink: Sink): SinkFactory =
    new NoParamSinkFactory(sink)

  class NoParamSinkFactory(sink: Sink) extends SinkFactory {
    @MethodToInvoke
    def create(): Sink = sink
  }

}
