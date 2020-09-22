package pl.touk.nussknacker.engine.definition.parameter

import pl.touk.nussknacker.engine.api.definition.{Parameter, ParameterValidator}
import pl.touk.nussknacker.engine.api.process.{ParameterConfig, SingleNodeConfig}
import pl.touk.nussknacker.engine.definition.parameter.editor.EditorExtractor
import pl.touk.nussknacker.engine.definition.parameter.validator.{EditorBasedValidatorExtractor, ValidatorExtractorParameters}

/*
  For parameters defined explicitly in code (e.g. by GenericNodeTransformation or using WithExplicitMethod) we want to define sensible fallback/defaults:
  - if no editor is defined in code, we take the one based by config or parameter type
  - if editor is defined, we add validator based on type
 */
object StandardParameterEnrichment {

  def enrichParameterDefinitions(original: List[Parameter], nodeConfig: SingleNodeConfig): List[Parameter] = {
    original.map(p => enrichParameter(p, nodeConfig.paramConfig(p.name)))
  }

  private def enrichParameter(original: Parameter, parameterConfig: ParameterConfig): Parameter = {
    val parameterData = ParameterData(original.typ, Nil)
    val finalEditor = original.editor.orElse(EditorExtractor.extract(ParameterData(original.typ, Nil), parameterConfig))
    val finalValidators = (original.validators ++ extractAdditionalValidator(parameterData, parameterConfig)).distinct
    original.copy(editor = finalEditor, validators = finalValidators)
  }


  private def extractAdditionalValidator(parameterData: ParameterData, parameterConfig: ParameterConfig): Option[ParameterValidator] = {
    val validatorExtractorParameters = ValidatorExtractorParameters(parameterData, isOptional = true, parameterConfig)
    EditorBasedValidatorExtractor.extract(validatorExtractorParameters)
  }
}
