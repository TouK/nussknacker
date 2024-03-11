package pl.touk.nussknacker.engine.definition.component.parameter

import pl.touk.nussknacker.engine.api.component.{ParameterConfig, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.definition.{Parameter, ParameterEditor, ParameterValidator}
import pl.touk.nussknacker.engine.definition.component.parameter.defaults.{
  DefaultValueDeterminerChain,
  DefaultValueDeterminerParameters
}
import pl.touk.nussknacker.engine.definition.component.parameter.editor.EditorExtractor
import pl.touk.nussknacker.engine.definition.component.parameter.validator.{
  EditorBasedValidatorExtractor,
  ValidatorExtractorParameters
}

/*
  For parameters defined explicitly in code (e.g. by DynamicComponent or using WithExplicitMethod) we want to define sensible fallback/defaults:
  - if no editor is defined in code, we take the one based by config or parameter type
  - if editor is defined, we add validator based on type
 */
object StandardParameterEnrichment {

  def enrichParameterDefinitions(
      original: List[Parameter],
      parametersConfig: Map[String, ParameterConfig]
  ): List[Parameter] = {
    original.map(p => enrichParameter(p, parametersConfig.getOrElse(p.name.value, ParameterConfig.empty)))
  }

  private def enrichParameter(original: Parameter, parameterConfig: ParameterConfig): Parameter = {
    val parameterData = ParameterData(original.typ, Nil)
    val finalEditor   = original.editor.orElse(EditorExtractor.extract(parameterData, parameterConfig))
    val finalValidators =
      (original.validators ++ extractAdditionalValidator(parameterData, parameterConfig, finalEditor)).distinct
    val isOptional = original.isOptional
    val finalDefaultValue = original.defaultValue.orElse(
      DefaultValueDeterminerChain.determineParameterDefaultValue(
        DefaultValueDeterminerParameters(parameterData, isOptional, parameterConfig, finalEditor)
      )
    )
    val finalHintText = original.hintText.orElse(parameterConfig.hintText)
    val finalLabel    = original.labelOpt.orElse(parameterConfig.label)

    original.copy(
      editor = finalEditor,
      validators = finalValidators,
      defaultValue = finalDefaultValue,
      hintText = finalHintText,
      labelOpt = finalLabel
    )
  }

  private def extractAdditionalValidator(
      parameterData: ParameterData,
      parameterConfig: ParameterConfig,
      finalEditor: Option[ParameterEditor]
  ): Option[ParameterValidator] = {
    val validatorExtractorParameters =
      ValidatorExtractorParameters(parameterData, isOptional = true, parameterConfig, finalEditor)
    EditorBasedValidatorExtractor.extract(validatorExtractorParameters)
  }

}
