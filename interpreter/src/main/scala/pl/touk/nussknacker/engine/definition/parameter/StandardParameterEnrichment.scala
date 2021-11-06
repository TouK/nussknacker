package pl.touk.nussknacker.engine.definition.parameter

import pl.touk.nussknacker.engine.api.component.SingleComponentConfig
import pl.touk.nussknacker.engine.api.definition.{MandatoryParameterValidator, NotBlankParameterValidator, Parameter, ParameterEditor, ParameterValidator}
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.definition.parameter.defaults.{DefaultValueDeterminerChain, DefaultValueDeterminerParameters}
import pl.touk.nussknacker.engine.definition.parameter.editor.EditorExtractor
import pl.touk.nussknacker.engine.definition.parameter.validator.{EditorBasedValidatorExtractor, ValidatorExtractorParameters}

/*
  For parameters defined explicitly in code (e.g. by GenericNodeTransformation or using WithExplicitMethod) we want to define sensible fallback/defaults:
  - if no editor is defined in code, we take the one based by config or parameter type
  - if editor is defined, we add validator based on type
 */
object StandardParameterEnrichment {

  def enrichParameterDefinitions(original: List[Parameter], componentConfig: SingleComponentConfig): List[Parameter] = {
    original.map(p => enrichParameter(p, componentConfig.paramConfig(p.name)))
  }

  private def enrichParameter(original: Parameter, parameterConfig: ParameterConfig): Parameter = {
    val parameterData = ParameterData(original.typ, Nil)
    val finalEditor = original.editor.orElse(EditorExtractor.extract(parameterData, parameterConfig))
    val finalValidators = determineValidatorss(original, parameterConfig, parameterData, finalEditor)
    val isOptional = original.isOptional
    val finalDefaultValue = original.defaultValue.orElse(DefaultValueDeterminerChain.determineParameterDefaultValue(
      DefaultValueDeterminerParameters(parameterData, isOptional, parameterConfig, finalEditor)))
    original.copy(editor = finalEditor, validators = finalValidators, defaultValue = finalDefaultValue)
  }

  private def determineValidatorss(original: Parameter, parameterConfig: ParameterConfig, parameterData: ParameterData, finalEditor: Option[ParameterEditor]) = {
    // In case if someone forget to specify validators and used only Parameter() or Parameter.optional(), we add missing forgotten validators
    // It can be tricky, because we have no option to specify that we don't want those "standard" validators
    if (original.validators.exists(_ != MandatoryParameterValidator))
      original.validators
    else
      (original.validators ++ extractAdditionalValidator(parameterData, parameterConfig, finalEditor)).distinct
  }

  private def extractAdditionalValidator(parameterData: ParameterData, parameterConfig: ParameterConfig, finalEditor: Option[ParameterEditor]): Option[ParameterValidator] = {
    val validatorExtractorParameters = ValidatorExtractorParameters(parameterData, isOptional = true, parameterConfig, finalEditor)
    EditorBasedValidatorExtractor.extract(validatorExtractorParameters)
  }

}
