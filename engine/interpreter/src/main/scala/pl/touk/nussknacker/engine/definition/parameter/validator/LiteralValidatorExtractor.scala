package pl.touk.nussknacker.engine.definition.parameter.validator

import pl.touk.nussknacker.engine.api.definition.{LiteralParameterValidator, ParameterValidator}
import pl.touk.nussknacker.engine.api.validation.Literal

object LiteralValidatorExtractor extends ValidatorExtractor {
  override def extract(params: ValidatorExtractorParameters): Option[ParameterValidator] =
    params.parameterData.getAnnotation[Literal]
      .flatMap(_ => LiteralParameterValidator(params.parameterData.typing))
}
