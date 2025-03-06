package pl.touk.nussknacker.engine.definition.component.parameter.validator

import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.definition.{ParameterEditors, ParameterValidator}
import pl.touk.nussknacker.engine.definition.component.parameter.ParameterData

trait ValidatorExtractor {

  def extract(params: ValidatorExtractorParameters): Option[ParameterValidator]

}

case class ValidatorExtractorParameters(
    parameterData: ParameterData,
    isOptional: Boolean,
    parameterConfig: ParameterConfig,
    extractedEditors: Option[ParameterEditors]
)
