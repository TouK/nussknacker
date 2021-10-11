package pl.touk.nussknacker.engine.standalone.api

import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{LazyParameter, LazyParameterInterpreter, MethodToInvoke, ParamName}

class StandaloneSinkFactory extends SinkFactory {

  @MethodToInvoke
  def invoke(@ParamName("value") value: LazyParameter[AnyRef]): Sink = new StandaloneSink {
    override def prepareResponse(implicit evaluateLazyParameter: LazyParameterInterpreter): LazyParameter[AnyRef] = value
  }

}

trait StandaloneSink extends Sink {

  def prepareResponse(implicit evaluateLazyParameter: LazyParameterInterpreter): LazyParameter[AnyRef]

}
