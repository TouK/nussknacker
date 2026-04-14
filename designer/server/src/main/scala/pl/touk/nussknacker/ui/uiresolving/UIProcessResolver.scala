package pl.touk.nussknacker.ui.uiresolving

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, CanonicalProcessConverter}
import pl.touk.nussknacker.engine.dict.ProcessDictSubstitutor
import pl.touk.nussknacker.restmodel.validation.ScenarioGraphWithValidationResult
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.ui.process.label.ScenarioLabel
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.validation.UIProcessValidator
import pl.touk.nussknacker.ui.validation.UIProcessValidator.ValidationResultWithDetails

/**
  * This class handles resolving of expression (e.g. dict labels resolved to keys) which should be done before
  * validation of process created in UI and before process save.
  * Also it handles "reverse" resolving process done before returning process to UI
  */
class UIProcessResolver(uiValidator: UIProcessValidator, substitutor: ProcessDictSubstitutor) {

  private val beforeUiResolvingValidator = uiValidator.transformValidator(_.withLabelsDictTyper)

  def validateAndResolve(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
      isFragment: Boolean,
  )(
      implicit loggedUser: LoggedUser
  ): CanonicalProcess = {
    val validationResult = validateBeforeUiResolving(scenarioGraph, processVersion, isFragment)
    resolveExpressions(scenarioGraph, processVersion.processName, validationResult.typingInfo)
  }

  def validateBeforeUiResolving(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
      isFragment: Boolean,
  )(
      implicit loggedUser: LoggedUser
  ): ValidationResult = {
    beforeUiResolvingValidator.validate(scenarioGraph, processVersion, isFragment)
  }

  def validateBeforeUiResolving(
      scenarioGraph: ScenarioGraph,
      processName: ProcessName,
      isFragment: Boolean,
      labels: List[ScenarioLabel]
  )(
      implicit loggedUser: LoggedUser
  ): ValidationResult = {
    beforeUiResolvingValidator.validate(scenarioGraph, processName, isFragment, labels)
  }

  def validateBeforeUiResolvingWithDetauls(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
      isFragment: Boolean,
  )(
      implicit loggedUser: LoggedUser
  ): ValidationResultWithDetails = {
    beforeUiResolvingValidator.validateWithDetails(scenarioGraph, processVersion, isFragment)
  }

  def resolveExpressions(
      scenarioGraph: ScenarioGraph,
      processName: ProcessName,
      typingInfo: Map[String, Map[String, ExpressionTypingInfo]]
  ): CanonicalProcess = {
    val canonical = CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, processName)
    substitutor.substitute(canonical, typingInfo)
  }

  def validateAndReverseResolve(
      canonical: CanonicalProcess,
      processVersion: ProcessVersion,
      isFragment: Boolean,
      removeDictLabels: Boolean,
  )(implicit loggedUser: LoggedUser): ScenarioGraphWithValidationResult = {
    val validationResult = validateBeforeUiReverseResolving(canonical, processVersion, isFragment)
    reverseResolveExpressions(canonical, processVersion, isFragment, validationResult, removeDictLabels)
  }

  def validateBeforeUiReverseResolving(
      canonical: CanonicalProcess,
      processVersion: ProcessVersion,
      isFragment: Boolean
  )(
      implicit loggedUser: LoggedUser
  ): ValidationResult =
    uiValidator.validateCanonicalProcess(canonical, processVersion, isFragment)

  private def reverseResolveExpressions(
      canonical: CanonicalProcess,
      processVersion: ProcessVersion,
      isFragment: Boolean,
      validationResult: ValidationResult,
      removeDictLabels: Boolean,
  ): ScenarioGraphWithValidationResult = {
    val substituted   = substitutor.reversed(removeDictLabels).substitute(canonical, validationResult.typingInfo)
    val scenarioGraph = substituted.toScenarioGraph
    val uiValidations =
      uiValidator.uiValidation(
        scenarioGraph,
        processVersion.processName,
        isFragment,
        processVersion.labels.map(ScenarioLabel.apply)
      )
    ScenarioGraphWithValidationResult(scenarioGraph, uiValidations.add(validationResult))
  }

}
