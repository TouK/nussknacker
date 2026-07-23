package pl.touk.nussknacker.engine.definition.component.parameter

import pl.touk.nussknacker.engine.ModelConfig.GlobalParametersConfig
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.definition.{
  MandatoryParameterValidator,
  Parameter,
  ParameterEditor,
  ParameterValidator,
  SpelParameterEditor
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.definition.component.parameter.defaults.{
  DefaultValueDeterminerChain,
  DefaultValueDeterminerParameters
}
import pl.touk.nussknacker.engine.definition.component.parameter.editor.EditorExtractor
import pl.touk.nussknacker.engine.definition.component.parameter.validator.{
  EditorBasedValidatorExtractor,
  ValidatorExtractorParameters
}
import pl.touk.nussknacker.engine.util.Implicits.RichIterable

/*
  For parameters defined explicitly in code (e.g. by DynamicComponent or using WithExplicitMethod) we want to define sensible fallback/defaults:
  - if no editor is defined in code, we take the one based by config or parameter type
  - if editor is defined, we add validator based on type
 */
object StandardParameterEnrichment {

  def enrichParameterDefinitions(
      original: List[Parameter],
      parametersConfig: Map[ParameterName, ParameterConfig],
      globalParametersConfig: GlobalParametersConfig
  ): List[Parameter] = {
    original.map(p =>
      enrichParameter(p, parametersConfig.getOrElse(p.name, ParameterConfig.empty), globalParametersConfig)
    )
  }

  private def enrichParameter(
      original: Parameter,
      parameterConfig: ParameterConfig,
      globalParametersConfig: GlobalParametersConfig
  ): Parameter = {
    val parameterData = ParameterData(original.typ, annotations = Nil, original.isLazyParameter)
    val finalEditors = original.editors
      .orElseIfEmpty(EditorExtractor.extract(parameterData, parameterConfig, globalParametersConfig))
    val finalValidators =
      (original.validators ++
        parameterConfig.validators.toList.flatten ++
        extractAdditionalValidator(parameterData, parameterConfig, finalEditors)).distinct
    val isOptional = !finalValidators.contains(MandatoryParameterValidator)
    val finalDefaultValue = original.defaultValue.orElse(
      DefaultValueDeterminerChain.determineParameterDefaultValue(
        DefaultValueDeterminerParameters(parameterData, isOptional, parameterConfig, finalEditors)
      )
    )
    val finalHintText = original.hintText.orElse(parameterConfig.hintText)
    val finalLabel    = original.labelOpt.orElse(parameterConfig.label)

    original.copy(
      editors = finalEditors,
      validators = finalValidators,
      defaultValue = finalDefaultValue,
      hintText = finalHintText,
      labelOpt = finalLabel
    )
  }

  private def extractAdditionalValidator(
      parameterData: ParameterData,
      parameterConfig: ParameterConfig,
      finalEditors: List[ParameterEditor]
  ): Option[ParameterValidator] = {
    val validatorExtractorParameters =
      ValidatorExtractorParameters(parameterData, isOptional = true, parameterConfig, finalEditors)
    EditorBasedValidatorExtractor.extract(validatorExtractorParameters)
  }

}
