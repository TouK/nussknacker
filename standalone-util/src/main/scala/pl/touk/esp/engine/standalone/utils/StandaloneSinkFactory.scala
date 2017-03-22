package pl.touk.esp.engine.standalone.utils

import pl.touk.esp.engine.api.MethodToInvoke
import pl.touk.esp.engine.api.process.{Sink, SinkFactory}

class StandaloneSinkFactory extends SinkFactory {

  @MethodToInvoke
  def invoke(): Sink = new Sink {
    override def testDataOutput: Option[(Any) => String] = Some(_.toString)
  }

}
