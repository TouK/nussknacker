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
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.validation.UIProcessValidator

/**
  * This class handles resolving of expression (e.g. dict labels resolved to keys) which should be done before
  * validation of process created in UI and before process save.
  * Also it handles "reverse" resolving process done before returning process to UI
  */
class UIProcessResolver(uiValidator: UIProcessValidator, substitutor: ProcessDictSubstitutor) {

  private val beforeUiResolvingValidator = uiValidator.withValidator(_.withExpressionParsers {
    case spel: SpelExpressionParser =>
      spel.typingDictLabels
  })

  def validateAndResolve(displayable: DisplayableProcess)(implicit loggedUser: LoggedUser): CanonicalProcess = {
    val validationResult = validateBeforeUiResolving(displayable)
    resolveExpressions(displayable, validationResult.typingInfo)
  }

  def validateBeforeUiResolving(displayable: DisplayableProcess)(implicit loggedUser: LoggedUser): ValidationResult = {
    beforeUiResolvingValidator.validate(displayable)
  }

  def resolveExpressions(
      displayable: DisplayableProcess,
      typingInfo: Map[String, Map[String, ExpressionTypingInfo]]
  ): CanonicalProcess = {
    val canonical = ProcessConverter.fromDisplayable(displayable)
    substitutor.substitute(canonical, typingInfo)
  }

  def validateAndReverseResolve(
      canonical: CanonicalProcess,
      processingType: ProcessingType,
      category: Category
  )(implicit loggedUser: LoggedUser): ValidatedDisplayableProcess = {
    val validationResult = validateBeforeUiReverseResolving(canonical, processingType)
    reverseResolveExpressions(canonical, processingType, category, validationResult)
  }

  def validateBeforeUiReverseResolving(canonical: CanonicalProcess, processingType: ProcessingType)(
      implicit loggedUser: LoggedUser
  ): ValidationResult =
    uiValidator.validateCanonicalProcess(canonical, processingType)

  private def reverseResolveExpressions(
      canonical: CanonicalProcess,
      processingType: ProcessingType,
      category: Category,
      validationResult: ValidationResult
  ): ValidatedDisplayableProcess = {
    val substituted   = substitutor.reversed.substitute(canonical, validationResult.typingInfo)
    val displayable   = ProcessConverter.toDisplayable(substituted, processingType, category)
    val uiValidations = uiValidator.uiValidation(displayable)
    ValidatedDisplayableProcess.withValidationResult(displayable, uiValidations.add(validationResult))
  }

}
