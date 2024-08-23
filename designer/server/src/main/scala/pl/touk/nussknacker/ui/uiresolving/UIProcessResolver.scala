package pl.touk.nussknacker.ui.uiresolving

import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.dict.ProcessDictSubstitutor
import pl.touk.nussknacker.restmodel.validation.ScenarioGraphWithValidationResult
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.ui.process.label.ScenarioLabel
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.validation.UIProcessValidator

/**
  * This class handles resolving of expression (e.g. dict labels resolved to keys) which should be done before
  * validation of process created in UI and before process save.
  * Also it handles "reverse" resolving process done before returning process to UI
  */
class UIProcessResolver(uiValidator: UIProcessValidator, substitutor: ProcessDictSubstitutor) {

  private val beforeUiResolvingValidator = uiValidator.transformValidator(_.withLabelsDictTyper)

  def validateAndResolve(
      scenarioGraph: ScenarioGraph,
      processName: ProcessName,
      isFragment: Boolean,
      labels: List[ScenarioLabel]
  )(
      implicit loggedUser: LoggedUser
  ): CanonicalProcess = {
    val validationResult = validateBeforeUiResolving(scenarioGraph, processName, isFragment, labels)
    resolveExpressions(scenarioGraph, processName, validationResult.typingInfo)
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
      processName: ProcessName,
      isFragment: Boolean,
      labels: List[ScenarioLabel]
  )(implicit loggedUser: LoggedUser): ScenarioGraphWithValidationResult = {
    val validationResult = validateBeforeUiReverseResolving(canonical, isFragment)
    reverseResolveExpressions(canonical, processName, isFragment, labels, validationResult)
  }

  def validateBeforeUiReverseResolving(canonical: CanonicalProcess, isFragment: Boolean)(
      implicit loggedUser: LoggedUser
  ): ValidationResult =
    uiValidator.validateCanonicalProcess(canonical, isFragment)

  private def reverseResolveExpressions(
      canonical: CanonicalProcess,
      processName: ProcessName,
      isFragment: Boolean,
      labels: List[ScenarioLabel],
      validationResult: ValidationResult
  ): ScenarioGraphWithValidationResult = {
    val substituted   = substitutor.reversed.substitute(canonical, validationResult.typingInfo)
    val scenarioGraph = CanonicalProcessConverter.toScenarioGraph(substituted)
    val uiValidations = uiValidator.uiValidation(scenarioGraph, processName, isFragment, labels)
    ScenarioGraphWithValidationResult(scenarioGraph, uiValidations.add(validationResult))
  }

}
