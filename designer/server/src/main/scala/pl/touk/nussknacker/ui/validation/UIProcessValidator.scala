package pl.touk.nussknacker.ui.validation

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.CustomProcessValidator
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.graph.{Edge, ScenarioGraph}
import pl.touk.nussknacker.engine.api.process.{ProcessingType, ProcessName}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.{IdValidator, NodeTypingInfo, ProcessValidator}
import pl.touk.nussknacker.engine.graph.node.{Disableable, FragmentInputDefinition, NodeData, Source}
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax._
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{
  NodeTypingData,
  UIGlobalError,
  ValidationErrors,
  ValidationResult
}
import pl.touk.nussknacker.ui.definition.{DefinitionsService, ScenarioPropertiesConfigFinalizer}
import pl.touk.nussknacker.ui.process.fragment.FragmentResolver
import pl.touk.nussknacker.ui.process.label.ScenarioLabel
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.security.api.LoggedUser

class UIProcessValidator(
    processingType: ProcessingType,
    validator: ProcessValidator,
    scenarioProperties: Map[String, ScenarioPropertyConfig],
    scenarioPropertiesConfigFinalizer: ScenarioPropertiesConfigFinalizer,
    scenarioLabelsValidator: ScenarioLabelsValidator,
    additionalValidators: List[CustomProcessValidator],
    fragmentResolver: FragmentResolver,
) {

  import pl.touk.nussknacker.engine.util.Implicits._

  private val scenarioPropertiesValidator =
    new ScenarioPropertiesValidator(scenarioProperties, scenarioPropertiesConfigFinalizer)

  def withFragmentResolver(fragmentResolver: FragmentResolver) =
    new UIProcessValidator(
      processingType,
      validator,
      scenarioProperties,
      scenarioPropertiesConfigFinalizer,
      scenarioLabelsValidator,
      additionalValidators,
      fragmentResolver
    )

  def transformValidator(transform: ProcessValidator => ProcessValidator) =
    new UIProcessValidator(
      processingType,
      transform(validator),
      scenarioProperties,
      scenarioPropertiesConfigFinalizer,
      scenarioLabelsValidator,
      additionalValidators,
      fragmentResolver
    )

  def validate(
      scenarioGraph: ScenarioGraph,
      processName: ProcessName,
      isFragment: Boolean,
      labels: List[ScenarioLabel]
  )(
      implicit loggedUser: LoggedUser
  ): ValidationResult = {
    val processVersion = ProcessVersion.empty.copy(
      processName = processName,
      labels = labels.map(_.value)
    )
    validate(scenarioGraph, processVersion, isFragment)
  }

  def validate(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
      isFragment: Boolean,
  )(
      implicit loggedUser: LoggedUser
  ): ValidationResult = {
    val uiValidationResult = uiValidation(
      scenarioGraph,
      processVersion.processName,
      isFragment,
      processVersion.labels.map(ScenarioLabel.apply)
    )

    // TODO: Enable further validation when save is not allowed
    // The problem preventing further validation is that loose nodes and their children are skipped during conversion
    // and in case if the scenario has only loose nodes, it will be reported that the scenario is empty
    if (uiValidationResult.saveAllowed) {
      val canonical = CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, processVersion.processName)
      // The deduplication is needed for errors that are validated on both uiValidation for DisplayableProcess and
      // CanonicalProcess validation.
      deduplicateErrors(uiValidationResult.add(validateCanonicalProcess(canonical, processVersion, isFragment)))
    } else {
      uiValidationResult
    }
  }

  // Some of these validations are duplicated with CanonicalProcess validations in order to show them in case when there
  // is an error preventing graph canonization. For example we want to display node and scenario id errors for scenarios
  // that have loose nodes. If you want to achieve this result, you need to add these validations here and deduplicate
  // resulting errors later.
  def uiValidation(
      scenarioGraph: ScenarioGraph,
      processName: ProcessName,
      isFragment: Boolean,
      labels: List[ScenarioLabel]
  ): ValidationResult = {
    validateScenarioName(processName, isFragment)
      .add(validateScenarioLabels(labels))
      .add(validateNodesId(scenarioGraph))
      .add(validateDuplicates(scenarioGraph))
      .add(validateLooseNodes(scenarioGraph))
      .add(validateEdgeUniqueness(scenarioGraph))
      .add(validateScenarioProperties(scenarioGraph.properties.additionalFields.properties, isFragment))
      .add(warningValidation(scenarioGraph))
  }

  def validateCanonicalProcess(
      canonical: CanonicalProcess,
      processVersion: ProcessVersion,
      isFragment: Boolean
  )(implicit loggedUser: LoggedUser): ValidationResult = {
    def validateAndFormatResult(scenario: CanonicalProcess) = {
      implicit val jobData: JobData = JobData(scenario.metaData, processVersion)
      val validated                 = validator.validate(scenario, isFragment)
      validated.result
        .fold(formatErrors, _ => ValidationResult.success)
        .withNodeResults(validated.typing.mapValuesNow(nodeInfoToResult))
    }

    // TODO: should we validate after resolve?
    val additionalValidatorErrors = additionalValidators
      .map(_.validate(canonical))
      .sequence
      .fold(formatErrors, _ => ValidationResult.success)

    val resolvedScenarioResult = fragmentResolver.resolveFragments(canonical, processingType)

    // TODO: handle types when fragment resolution fails
    val validationResult = resolvedScenarioResult match {
      case Invalid(fragmentResolutionErrors) => formatErrors(fragmentResolutionErrors)
      case Valid(scenario) =>
        val validationResult = validateAndFormatResult(scenario)
        val containsDisabledNodes = canonical.collectAllNodes.exists {
          case nodeData: Disableable if nodeData.isDisabled.contains(true) => true
          case _                                                           => false
        }
        if (containsDisabledNodes) {
          val resolvedScenarioWithoutDisabledNodes =
            fragmentResolver.resolveFragments(canonical.withoutDisabledNodes, processingType)
          resolvedScenarioWithoutDisabledNodes match {
            case Invalid(fragmentResolutionErrors)   => formatErrors(fragmentResolutionErrors)
            case Valid(scenarioWithoutDisabledNodes) =>
              // FIXME: Validation errors for fragment nodes are not properly handled by FE
              // We add typing data from disabled nodes to have typing and suggestions for expressions in disabled nodes
              val resultWithoutDisabledNodes = validateAndFormatResult(scenarioWithoutDisabledNodes)
              resultWithoutDisabledNodes.copy(nodeResults = validationResult.nodeResults)
          }
        } else {
          validationResult
        }
    }
    validationResult.add(additionalValidatorErrors)
  }

  private def nodeInfoToResult(typingInfo: NodeTypingInfo) = NodeTypingData(
    typingInfo.inputValidationContext.localVariables,
    typingInfo.parameters.map(_.map(DefinitionsService.createUIParameter)),
    typingInfo.expressionsTypingInfo
  )

  private def warningValidation(process: ScenarioGraph): ValidationResult = {
    val disabledNodes = process.nodes.collect {
      case d: NodeData with Disableable if d.isDisabled.getOrElse(false) => d
    }
    val disabledNodesWarnings =
      disabledNodes.map(node => (node.id, List(PrettyValidationErrors.formatErrorMessage(DisabledNode(node.id))))).toMap
    ValidationResult.warnings(disabledNodesWarnings)
  }

  private def validateScenarioName(processName: ProcessName, isFragment: Boolean): ValidationResult = {
    IdValidator.validateScenarioName(processName, isFragment) match {
      case Valid(_)   => ValidationResult.success
      case Invalid(e) => formatErrors(e)
    }
  }

  private def validateNodesId(scenarioGraph: ScenarioGraph): ValidationResult = {
    val nodeIdErrors = scenarioGraph.nodes
      .map(n => IdValidator.validateNodeId(n.id))
      .collect { case Invalid(e) =>
        e
      }
      .reduceOption(_ concatNel _)

    nodeIdErrors match {
      case Some(value) => formatErrors(value)
      case None        => ValidationResult.success
    }
  }

  private def validateScenarioLabels(labels: List[ScenarioLabel]): ValidationResult = {
    scenarioLabelsValidator.validate(labels) match {
      case Valid(()) =>
        ValidationResult.success
      case Invalid(errors) =>
        ValidationResult.globalErrors(
          errors
            .map(ve =>
              ScenarioLabelValidationError(label = ve.label, description = ve.validationMessages.toList.mkString(", "))
            )
            .map(PrettyValidationErrors.formatErrorMessage)
            .map(UIGlobalError(_, nodeIds = List.empty))
            .toList
        )
    }
  }

  private def validateScenarioProperties(
      properties: Map[String, String],
      isFragment: Boolean
  ): ValidationResult = {
    if (isFragment) {
      ValidationResult.success
    } else {
      scenarioPropertiesValidator.validate(properties.toList)
    }
  }

  private def validateEdgeUniqueness(scenarioGraph: ScenarioGraph): ValidationResult = {
    val edgesByFrom = scenarioGraph.edges.groupBy(_.from)

    def findNonUniqueEdge(nodeId: String, edgesFromNode: List[Edge]) = {
      val nonUniqueByType = edgesFromNode.groupBy(_.edgeType).collect {
        case (Some(eType), list) if eType.mustBeUnique && list.size > 1 =>
          PrettyValidationErrors.formatErrorMessage(NonUniqueEdgeType(eType.toString, nodeId))
      }
      val nonUniqueByTarget = edgesFromNode.groupBy(_.to).collect {
        case (to, list) if list.size > 1 =>
          PrettyValidationErrors.formatErrorMessage(NonUniqueEdge(nodeId, to))
      }
      (nonUniqueByType ++ nonUniqueByTarget).toList
    }

    val edgeUniquenessErrors =
      edgesByFrom.map { case (from, edges) => from -> findNonUniqueEdge(from, edges) }.filterNot(_._2.isEmpty)
    ValidationResult.errors(edgeUniquenessErrors, List(), List())
  }

  private def validateLooseNodes(scenarioGraph: ScenarioGraph): ValidationResult = {
    val looseNodesIds = scenarioGraph.nodes
      // source & fragment inputs don't have inputs
      .filterNot(n => n.isInstanceOf[FragmentInputDefinition] || n.isInstanceOf[Source])
      .filterNot(n => scenarioGraph.edges.exists(_.to == n.id))
      .map(_.id)

    if (looseNodesIds.isEmpty) {
      ValidationResult.success
    } else {
      formatErrors(NonEmptyList.one(LooseNode(looseNodesIds.toSet)))
    }
  }

  private def validateDuplicates(scenarioGraph: ScenarioGraph): ValidationResult = {
    val nodeIds    = scenarioGraph.nodes.map(_.id)
    val duplicates = nodeIds.groupBy(identity).filter(_._2.size > 1).keys.toList

    if (duplicates.isEmpty) {
      ValidationResult.success
    } else {
      formatErrors(NonEmptyList.one(DuplicatedNodeIds(duplicates.toSet)))
    }
  }

  private def formatErrors(errors: NonEmptyList[ProcessCompilationError]): ValidationResult = {
    val globalErrors     = errors.filter(_.isInstanceOf[ScenarioGraphLevelError])
    val propertiesErrors = errors.filter(_.isInstanceOf[ScenarioPropertiesError])
    val nodeErrors = errors.filter { e =>
      !globalErrors.contains(e) && !propertiesErrors.contains(e)
    }

    ValidationResult.errors(
      invalidNodes = (for {
        error  <- nodeErrors
        nodeId <- error.nodeIds
      } yield nodeId -> PrettyValidationErrors.formatErrorMessage(error)).toGroupedMap,
      processPropertiesErrors = propertiesErrors.map(e => PrettyValidationErrors.formatErrorMessage(e)),
      globalErrors =
        globalErrors.map(e => UIGlobalError(PrettyValidationErrors.formatErrorMessage(e), e.nodeIds.toList))
    )
  }

  private def deduplicateErrors(result: ValidationResult): ValidationResult = {
    val deduplicatedInvalidNodes = result.errors.invalidNodes.map { case (key, value) => key -> value.distinct }
    val deduplicatedProcessPropertiesErrors = result.errors.processPropertiesErrors.distinct
    val deduplicatedGlobalErrors            = result.errors.globalErrors.distinct

    result.copy(errors =
      ValidationErrors(
        invalidNodes = deduplicatedInvalidNodes,
        processPropertiesErrors = deduplicatedProcessPropertiesErrors,
        globalErrors = deduplicatedGlobalErrors
      )
    )
  }

}
