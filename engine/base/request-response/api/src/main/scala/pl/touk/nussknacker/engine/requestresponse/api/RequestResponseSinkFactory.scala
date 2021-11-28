package pl.touk.nussknacker.engine.requestresponse.api

import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{LazyParameter, LazyParameterInterpreter, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.baseengine.api.customComponentTypes.LiteSink
import pl.touk.nussknacker.engine.baseengine.api.utils.sinks.LazyParamSink

class RequestResponseSinkFactory extends SinkFactory {

  @MethodToInvoke
  def invoke(@ParamName("value") value: LazyParameter[AnyRef]): Sink = new LazyParamSink[AnyRef] {

    override def prepareResponse(implicit evaluateLazyParameter: LazyParameterInterpreter): LazyParameter[AnyRef] = value
  }

}

object RequestResponseSinkFactory {
  type RequestResponseSink = LiteSink[AnyRef]
}
