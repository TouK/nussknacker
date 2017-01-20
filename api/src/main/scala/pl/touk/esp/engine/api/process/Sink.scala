package pl.touk.esp.engine.api.process


trait Sink {

  def testDataOutput: Option[Any => String]

}

trait SinkFactory extends Serializable {

}

object SinkFactory {

  def noParam(sink: Sink): SinkFactory =
    new NoParamSinkFactory(sink)

  class NoParamSinkFactory(sink: Sink) extends SinkFactory {
    def create(): Sink = sink
  }

}