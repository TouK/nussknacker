package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

sealed trait ParameterEvaluationResult

sealed trait EagerParameterEvaluationResult extends ParameterEvaluationResult

sealed trait LazyParameterEvaluationResult extends ParameterEvaluationResult

case class SingleLazyParameterEvaluationResult(lazyParameter: LazyParameter[Nothing])
    extends LazyParameterEvaluationResult {
  def returnType: TypingResult = lazyParameter.returnType
}

case class SingleEagerParameterEvaluationResult(value: Any, returnType: TypingResult)
    extends EagerParameterEvaluationResult

case class BranchLazyParameterEvaluationResult(lazyParamByBranchId: Map[String, LazyParameter[Nothing]])
    extends LazyParameterEvaluationResult

case class BranchEagerParameterEvaluationResult(
    valueByBranchId: Map[String, Any],
    returnTypeByBranchId: Map[String, TypingResult]
) extends EagerParameterEvaluationResult
