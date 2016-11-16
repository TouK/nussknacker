package pl.touk.esp.engine.api.process


trait Sink {


}

trait SinkFactory {

}

object SinkFactory {

  def noParam(sink: Sink): SinkFactory =
    new NoParamSinkFactory(sink)

  class NoParamSinkFactory(sink: Sink) extends SinkFactory {
    def create(): Sink = sink
  }

}