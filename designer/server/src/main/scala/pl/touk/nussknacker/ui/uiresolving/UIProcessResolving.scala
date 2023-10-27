package pl.touk.nussknacker.ui.uiresolving

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.fixedvaluespresets.FixedValuesPresetProvider
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.dict.ProcessDictSubstitutor
import pl.touk.nussknacker.engine.graph.node.{FragmentInput, FragmentInputDefinition}
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{
  FragmentParameter,
  FragmentParameterFixedListPreset
}
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{
  NodeValidationError,
  NodeValidationErrorType,
  ValidationResult
}
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.validation.ProcessValidation

import scala.collection.mutable

/**
  * This class handles resolving of expression (e.g. dict labels resolved to keys) and fixed value presets
  * which should be done before validation of process created in UI and before process save.
  * Also it handles "reverse" resolving process done before returning process to UI
  */
class UIProcessResolving(
    validation: ProcessValidation,
    substitutorByProcessingType: ProcessingTypeDataProvider[ProcessDictSubstitutor, _],
    fixedValuesPresetProvider: FixedValuesPresetProvider
) extends LazyLogging {

  def validateBeforeExpressionResolving(displayable: DisplayableProcess): ValidationResult = {
    val v = validation.withExpressionParsers { case spel: SpelExpressionParser =>
      spel.typingDictLabels
    }
    v.validate(displayable)
  }

  def resolveExpressions(
      displayable: DisplayableProcess,
      typingInfo: Map[String, Map[String, ExpressionTypingInfo]]
  ): CanonicalProcess = {
    val canonical = ProcessConverter.fromDisplayable(displayable)
    substitutorByProcessingType
      .forType(displayable.processingType)
      .map(_.substitute(canonical, typingInfo))
      .getOrElse(canonical)
  }

  def validateBeforeExpressionReverseResolving(
      canonical: CanonicalProcess,
      processingType: ProcessingType,
      category: Category
  ): ValidationResult =
    validation.processingTypeValidationWithTypingInfo(canonical, processingType, category)

  def reverseResolveExpressions(
      canonical: CanonicalProcess,
      processingType: ProcessingType,
      category: Category,
      validationResult: ValidationResult
  ): ValidatedDisplayableProcess = {
    val substituted = substitutorByProcessingType
      .forType(processingType)
      .map(_.reversed.substitute(canonical, validationResult.typingInfo))
      .getOrElse(canonical)
    val displayable   = ProcessConverter.toDisplayable(substituted, processingType, category)
    val uiValidations = validation.uiValidation(displayable)
    ValidatedDisplayableProcess(displayable, uiValidations.add(validationResult))
  }

  def resolveFixedValuesPresets(process: DisplayableProcess): (DisplayableProcess, ValidationResult) = {
    val fixedValuesPresets = // TODO maybe this whole try/catch is overzealous, better to just let it 500?
      try {
        fixedValuesPresetProvider.getAll
      } catch {
        case e: Throwable =>
          logger.warn(s"FixedValuesPresetsProvider failed to provide presets ", e)
          return (
            process,
            ValidationResult.globalErrors(
              List(
                NodeValidationError(
                  "FixedValuesPresetsProviderFailure",
                  "FixedValuesPresetsProvider failed to provide presets",
                  "FixedValuesPresetsProvider failed to provide presets",
                  None,
                  NodeValidationErrorType.SaveAllowed
                )
              )
            )
          )
      }

    val nodeErrors = mutable.Map[String, List[NodeValidationError]]()
    val processWithEffectivePresets = process.copy(
      nodes = process.nodes.map {
        case fragmentInput: FragmentInput =>
          fragmentInput.copy(
            fragmentParams = fragmentInput.fragmentParams.map(_.map {
              resolvePresetsInFragmentParameter(_, fragmentInput.id, fixedValuesPresets, nodeErrors)
            })
          )
        case fragmentInputDefinition: FragmentInputDefinition =>
          fragmentInputDefinition.copy(
            parameters = fragmentInputDefinition.parameters.map {
              resolvePresetsInFragmentParameter(_, fragmentInputDefinition.id, fixedValuesPresets, nodeErrors)
            }
          )
        case node => node
      }
    )

    (processWithEffectivePresets, ValidationResult.errors(nodeErrors.toMap, List.empty, List.empty))
  }

  private def resolvePresetsInFragmentParameter(
      fragmentParam: FragmentParameter,
      nodeId: String,
      fixedValuesPresets: Map[String, List[FixedExpressionValue]],
      nodeErrors: mutable.Map[String, List[NodeValidationError]]
  ) =
    fragmentParam match {
      case paramWithPreset: FragmentParameterFixedListPreset =>
        fixedValuesPresets.get(paramWithPreset.fixedValuesListPresetId) match {
          case Some(preset) =>
            paramWithPreset.copy(effectiveFixedValuesList =
              preset.map(v => FragmentInputDefinition.FixedExpressionValue(v.expression, v.label))
            )
          case None =>
            nodeErrors(nodeId) = NodeValidationError(
              "FixedValuesPresetNotFound",
              s"Preset with id='${paramWithPreset.fixedValuesListPresetId}' not found",
              s"Preset with id='${paramWithPreset.fixedValuesListPresetId}' not found",
              Some(paramWithPreset.name),
              NodeValidationErrorType.SaveAllowed
            ) :: nodeErrors.getOrElse(nodeId, List.empty)

            paramWithPreset.copy(effectiveFixedValuesList =
              List.empty // clear effectiveFixedValuesList instead of possibly using outdated presets
            )
        }

      case p => p
    }

}
