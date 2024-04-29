package pl.touk.nussknacker.engine.definition.component.parameter.validator

import pl.touk.nussknacker.engine.api.definition.{CompileTimeEvaluableValueValidator, ParameterValidator}
import pl.touk.nussknacker.engine.api.validation.CompileTimeEvaluableValue

object CompileTimeEvaluableValueValidatorExtractor extends ValidatorExtractor {

  override def extract(params: ValidatorExtractorParameters): Option[ParameterValidator] =
    params.parameterData
      .getAnnotation[CompileTimeEvaluableValue]
      .map(_ => CompileTimeEvaluableValueValidator)

}
