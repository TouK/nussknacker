package pl.touk.nussknacker.engine.standalone.api

import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{LazyParameter, LazyParameterInterpreter, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.baseengine.api.customComponentTypes.BaseEngineSink
import pl.touk.nussknacker.engine.baseengine.api.utils.sinks.LazyParamSink

class StandaloneSinkFactory extends SinkFactory {

  @MethodToInvoke
  def invoke(@ParamName("value") value: LazyParameter[AnyRef]): Sink = new LazyParamSink[AnyRef] {

    override def prepareResponse(implicit evaluateLazyParameter: LazyParameterInterpreter): LazyParameter[AnyRef] = value
  }

}

object StandaloneSinkFactory {
  type StandaloneSink = BaseEngineSink[AnyRef]
}
