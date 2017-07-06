package pl.touk.esp.engine.api.process

import pl.touk.esp.engine.api.MethodToInvoke


trait Sink {

  //remove Option from return type? or maybe Displayable instead of String?
  def testDataOutput: Option[Any => String]

}

/**
  * [[pl.touk.esp.engine.api.process.SinkFactory]] has to have method annotated with [[pl.touk.esp.engine.api.MethodToInvoke]]
  * that returns [[pl.touk.esp.engine.api.process.Sink]]
* */
trait SinkFactory extends Serializable {

}

object SinkFactory {

  def noParam(sink: Sink): SinkFactory =
    new NoParamSinkFactory(sink)

  class NoParamSinkFactory(sink: Sink) extends SinkFactory {
    @MethodToInvoke
    def create(): Sink = sink
  }

}