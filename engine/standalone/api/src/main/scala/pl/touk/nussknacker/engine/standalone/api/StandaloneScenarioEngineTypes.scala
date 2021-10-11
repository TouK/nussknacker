package pl.touk.nussknacker.engine.standalone.api

import pl.touk.nussknacker.engine.api.LazyParameterInterpreter
import pl.touk.nussknacker.engine.baseengine.api.BaseScenarioEngineTypes

import scala.concurrent.{ExecutionContext, Future}

case class StandaloneExtraData(lazyParameterHelper: LazyParameterInterpreter, ec: ExecutionContext)

object StandaloneScenarioEngineTypes {

  trait StandaloneCustomTransformer extends BaseScenarioEngineTypes.CustomTransformer[Future]

  trait StandaloneJoinCustomTransformer extends BaseScenarioEngineTypes.JoinCustomTransformer[Future]

}
