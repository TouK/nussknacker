package pl.touk.nussknacker.engine.standalone.api

import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{LazyParameter, LazyParameterInterpreter, MethodToInvoke}

class StandaloneSinkFactory extends SinkFactory {

  @MethodToInvoke
  def invoke(): Sink = new Sink {
    override def testDataOutput: Option[Any => String] = Some(_.toString)
  }

}

trait StandaloneSinkWithParameters extends Sink {

  //TODO: enable using outputExpression?
  def prepareResponse(implicit evaluateLazyParameter: LazyParameterInterpreter): LazyParameter[AnyRef]

}
