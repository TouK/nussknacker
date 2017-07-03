package pl.touk.esp.engine.api.process


trait Sink {

  //a moze tutaj nie bawic sie w option tylko zawsze cos miec?? ew. czy nie zadac Displayable?
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