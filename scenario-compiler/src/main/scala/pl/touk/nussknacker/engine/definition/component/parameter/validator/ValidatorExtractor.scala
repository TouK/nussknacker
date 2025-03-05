package pl.touk.nussknacker.engine.definition.component.parameter.validator

import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.definition.{ParameterEditor, ParameterValidator}
import pl.touk.nussknacker.engine.definition.component.parameter.ParameterData

trait ValidatorExtractor {

  def extract(params: ValidatorExtractorParameters): Option[ParameterValidator]

}

case class ValidatorExtractorParameters(
    parameterData: ParameterData,
    isOptional: Boolean,
    parameterConfig: ParameterConfig,
    extractedEditor: Option[ParameterEditor]
)
