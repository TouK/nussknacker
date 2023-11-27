package pl.touk.nussknacker.ui.uiresolving

import pl.touk.nussknacker.engine.api.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.dict.ProcessDictSubstitutor
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.restmodel.validation.ValidatedDisplayableProcess
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.validation.UIProcessValidator

/**
  * This class handles resolving of expression (e.g. dict labels resolved to keys) which should be done before
  * validation of process created in UI and before process save.
  * Also it handles "reverse" resolving process done before returning process to UI
  */
class UIProcessResolver(
                          validator: UIProcessValidator,
                          substitutorByProcessingType: ProcessingTypeDataProvider[ProcessDictSubstitutor, _]
) {

  def validateBeforeUiResolving(displayable: DisplayableProcess): ValidationResult = {
    val v = validator.withExpressionParsers { case spel: SpelExpressionParser =>
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

  def validateBeforeUiReverseResolving(
      canonical: CanonicalProcess,
      processingType: ProcessingType,
      category: Category
  ): ValidationResult =
    validator.processingTypeValidationWithTypingInfo(canonical, processingType, category)

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
    val uiValidations = validator.uiValidation(displayable)
    ValidatedDisplayableProcess.withValidationResult(displayable, uiValidations.add(validationResult))
  }

}
