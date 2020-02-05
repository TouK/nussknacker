package pl.touk.nussknacker.engine.definition.validator

import java.lang.reflect.Parameter

import pl.touk.nussknacker.engine.api.definition.ParameterValidator

object ValidatorsEvaluatorChain {

  def evaluate(param: Parameter): List[ParameterValidator] = {
    val evaluators = Seq(NotEmptyValidatorEvaluator)
    new ValidatorsEvaluatorChain(evaluators).evaluate(param)
  }
}

class ValidatorsEvaluatorChain(evaluators: Iterable[ValidatorEvaluator]) {

  def evaluate(parameter: Parameter): List[ParameterValidator] = {
    evaluators.flatMap(_.evaluate(parameter)).toList
  }
}
