package pl.touk.nussknacker.ui.validation

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.engine.api.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.{IdValidator, NodeTypingInfo, ProcessValidator}
import pl.touk.nussknacker.engine.graph.node.{Disableable, FragmentInputDefinition, NodeData, Source}
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax._
import pl.touk.nussknacker.engine.{CustomProcessValidator, ModelData}
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeTypingData, ValidationErrors, ValidationResult}
import pl.touk.nussknacker.ui.definition.DefinitionsService
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.fragment.FragmentResolver
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.security.api.LoggedUser

class UIProcessValidator(
    validator: ProcessValidator,
    scenarioProperties: Map[String, ScenarioPropertyConfig],
    additionalValidators: List[CustomProcessValidator],
    fragmentResolver: FragmentResolver,
) {

  import pl.touk.nussknacker.engine.util.Implicits._

  private val scenarioPropertiesValidator = new ScenarioPropertiesValidator(scenarioProperties)

  def withFragmentResolver(fragmentResolver: FragmentResolver) =
    new UIProcessValidator(validator, scenarioProperties, additionalValidators, fragmentResolver)

  def withValidator(transformValidator: ProcessValidator => ProcessValidator) =
    new UIProcessValidator(transformValidator(validator), scenarioProperties, additionalValidators, fragmentResolver)

  def withScenarioPropertiesConfig(scenarioPropertiesConfig: Map[String, ScenarioPropertyConfig]) =
    new UIProcessValidator(validator, scenarioPropertiesConfig, additionalValidators, fragmentResolver)

  def validate(displayable: DisplayableProcess)(implicit loggedUser: LoggedUser): ValidationResult = {
    val uiValidationResult = uiValidation(displayable)

    // there is no point in further validations if ui process structure is invalid
    // displayable to canonical conversion for invalid ui process structure can have unexpected results
    if (uiValidationResult.saveAllowed) {
      val canonical = ProcessConverter.fromDisplayable(displayable)
      // The deduplication is needed for errors that are validated on both uiValidation for DisplayableProcess and
      // CanonicalProcess validation.
      deduplicateErrors(uiValidationResult.add(validateCanonicalProcess(canonical, displayable.processingType)))
    } else {
      uiValidationResult
    }
  }

  // Some of these validations are duplicated with CanonicalProcess validations in order to show them in case when there
  // is an error preventing graph canonization. For example we want to display node and scenario id errors for scenarios
  // that have loose nodes. If you want to achieve this result, you need to add these validations here and deduplicate
  // resulting errors later.
  def uiValidation(displayable: DisplayableProcess): ValidationResult = {
    validateScenarioName(displayable)
      .add(validateNodesId(displayable))
      .add(validateDuplicates(displayable))
      .add(validateLooseNodes(displayable))
      .add(validateEdgeUniqueness(displayable))
      .add(validateScenarioProperties(displayable))
      .add(warningValidation(displayable))
  }

  def validateCanonicalProcess(
      canonical: CanonicalProcess,
      processingType: ProcessingType
  )(implicit loggedUser: LoggedUser): ValidationResult = {
    def validateAndFormatResult(scenario: CanonicalProcess) = {
      val validated = validator.validate(scenario)
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

        val containsDisabledNodes =
          canonical.withoutDisabledNodes.collectAllNodes.size != canonical.collectAllNodes.size
        if (containsDisabledNodes) {

          val resolvedScenarioWithoutDisabledNodes =
            fragmentResolver.resolveFragments(canonical.withoutDisabledNodes, processingType)

          resolvedScenarioWithoutDisabledNodes match {
            case Invalid(e)                          => formatErrors(e)
            case Valid(scenarioWithoutDisabledNodes) =>
              // FIXME: Validation errors for fragment nodes are not properly handled by FE
              // We add typing data from disabled nodes to have typing and suggestions for expressions in disabled nodes
              val resultWithoutDisabledNodes = validateAndFormatResult(scenarioWithoutDisabledNodes)
              resultWithoutDisabledNodes.copy(nodeResults =
                resultWithoutDisabledNodes.nodeResults ++ validationResult.nodeResults
              )
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

  private def warningValidation(process: DisplayableProcess): ValidationResult = {
    val disabledNodes = process.nodes.collect {
      case d: NodeData with Disableable if d.isDisabled.getOrElse(false) => d
    }
    val disabledNodesWarnings =
      disabledNodes.map(node => (node.id, List(PrettyValidationErrors.formatErrorMessage(DisabledNode(node.id))))).toMap
    ValidationResult.warnings(disabledNodesWarnings)
  }

  private def validateScenarioName(displayable: DisplayableProcess): ValidationResult = {
    IdValidator.validateScenarioName(displayable.name, displayable.metaData.isFragment) match {
      case Valid(_)   => ValidationResult.success
      case Invalid(e) => formatErrors(e)
    }
  }

  private def validateNodesId(displayable: DisplayableProcess): ValidationResult = {
    val nodeIdErrors = displayable.nodes
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

  private def validateScenarioProperties(
      displayable: DisplayableProcess
  ): ValidationResult = {
    if (displayable.metaData.isFragment) {
      ValidationResult.success
    } else {
      scenarioPropertiesValidator.validate(displayable)
    }
  }

  private def validateEdgeUniqueness(displayableProcess: DisplayableProcess): ValidationResult = {
    val edgesByFrom = displayableProcess.edges.groupBy(_.from)

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

  private def validateLooseNodes(displayableProcess: DisplayableProcess): ValidationResult = {
    val looseNodes = displayableProcess.nodes
      // source & fragment inputs don't have inputs
      .filterNot(n => n.isInstanceOf[FragmentInputDefinition] || n.isInstanceOf[Source])
      .filterNot(n => displayableProcess.edges.exists(_.to == n.id))
      .map(n => n.id -> List(PrettyValidationErrors.formatErrorMessage(LooseNode(n.id))))
      .toMap
    ValidationResult.errors(looseNodes, List(), List())
  }

  private def validateDuplicates(displayable: DisplayableProcess): ValidationResult = {
    val nodeIds    = displayable.nodes.map(_.id)
    val duplicates = nodeIds.groupBy(identity).filter(_._2.size > 1).keys.toList

    if (duplicates.isEmpty) {
      ValidationResult.success
    } else {
      ValidationResult.errors(
        Map(),
        List(),
        List(PrettyValidationErrors.formatErrorMessage(DuplicatedNodeIds(duplicates.toSet)))
      )
    }
  }

  private def formatErrors(errors: NonEmptyList[ProcessCompilationError]): ValidationResult = {
    val processErrors                   = errors.filter(_.nodeIds.isEmpty)
    val (propertiesErrors, otherErrors) = processErrors.partition(_.isInstanceOf[ScenarioPropertiesError])

    ValidationResult.errors(
      invalidNodes = (for {
        error  <- errors.toList.filterNot(processErrors.contains)
        nodeId <- error.nodeIds
      } yield nodeId -> PrettyValidationErrors.formatErrorMessage(error)).toGroupedMap,
      processPropertiesErrors = propertiesErrors.map(PrettyValidationErrors.formatErrorMessage),
      globalErrors = otherErrors.map(PrettyValidationErrors.formatErrorMessage)
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

  private sealed case class ValidatorKey(modelData: ModelData, category: Category)
}
