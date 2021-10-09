package pl.touk.nussknacker.engine.standalone.api

import pl.touk.nussknacker.engine.api.{InterpretationResult, LazyParameterInterpreter}

import scala.concurrent.{ExecutionContext, Future}

case class StandaloneExtraData(lazyParameterHelper: LazyParameterInterpreter, ec: ExecutionContext)

object StandaloneScenarioEngineTypes extends BaseScenarioEngineTypes[Future, InterpretationResult] {
  override def toRes(interpretationResult: InterpretationResult): InterpretationResult = interpretationResult
}
