package pl.touk.nussknacker.engine.standalone.utils

import pl.touk.nussknacker.engine.api.MethodToInvoke
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}

class StandaloneSinkFactory extends SinkFactory {

  @MethodToInvoke
  def invoke(): Sink = new Sink {
    override def testDataOutput: Option[(Any) => String] = Some(_.toString)
  }

}
