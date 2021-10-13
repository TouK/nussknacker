package pl.touk.nussknacker.engine.standalone.api

import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{LazyParameter, LazyParameterInterpreter, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.baseengine.api.BaseScenarioEngineTypes.BaseEngineSink

class StandaloneSinkFactory extends SinkFactory {

  @MethodToInvoke
  def invoke(@ParamName("value") value: LazyParameter[AnyRef]): Sink = new StandaloneSink {
    override def prepareResponse(implicit evaluateLazyParameter: LazyParameterInterpreter): LazyParameter[AnyRef] = value
  }

}

trait StandaloneSink extends BaseEngineSink[AnyRef]
