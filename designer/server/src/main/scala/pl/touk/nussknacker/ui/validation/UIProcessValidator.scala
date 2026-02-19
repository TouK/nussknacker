package pl.touk.nussknacker.ui.validation

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import cats.effect.SyncIO
import cats.effect.kernel.Resource
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.{CustomProcessValidator, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.api.{JobData, NodeId, ProcessVersion}
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.graph.{Edge, ScenarioGraph}
import pl.touk.nussknacker.engine.api.process.{ProcessingType, ProcessName}
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, CanonicalProcessConverter}
import pl.touk.nussknacker.engine.compile.{IdValidator, NodeTypingInfo, ProcessValidator}
import pl.touk.nussknacker.engine.graph.node.{Disableable, FragmentInputDefinition, NodeData, Source}
import pl.touk.nussknacker.engine.test.testcase.TestCases
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax._
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{
  NodeTypingData,
  UIGlobalError,
  ValidationErrors,
  ValidationResult
}
import pl.touk.nussknacker.ui.api.description.stickynotes.StickyNotesSettings
import pl.touk.nussknacker.ui.definition.{DefinitionsService, ScenarioPropertiesConfigFinalizer}
import pl.touk.nussknacker.ui.process.fragment.FragmentResolver
import pl.touk.nussknacker.ui.process.label.ScenarioLabel
import pl.touk.nussknacker.ui.process.test.testcase.validation.TestCaseValidator
import pl.touk.nussknacker.ui.security.api.LoggedUser

class UIProcessValidator(
    processingType: ProcessingType,
    validator: ProcessValidator,
    testCaseValidator: TestCaseValidator,
    scenarioProperties: Map[String, ScenarioPropertyConfig],
    scenarioPropertiesConfigFinalizer: ScenarioPropertiesConfigFinalizer,
    engineScenarioCompilationDependenciesResource: Resource[SyncIO, EngineScenarioCompilationDependencies],
    scenarioLabelsValidator: ScenarioLabelsValidator,
    additionalValidators: List[CustomProcessValidator],
    fragmentResolver: FragmentResolver,
    stickyNotesSettings: StickyNotesSettings,
) extends LazyLogging {

  import pl.touk.nussknacker.engine.util.Implicits._

  private val scenarioPropertiesValidator =
    new ScenarioPropertiesValidator(scenarioProperties, scenarioPropertiesConfigFinalizer)

  def withFragmentResolver(fragmentResolver: FragmentResolver) =
    new UIProcessValidator(
      processingType,
      validator,
      testCaseValidator,
      scenarioProperties,
      scenarioPropertiesConfigFinalizer,
      engineScenarioCompilationDependenciesResource,
      scenarioLabelsValidator,
      additionalValidators,
      fragmentResolver,
      stickyNotesSettings
    )

  def transformValidator(transform: ProcessValidator => ProcessValidator) =
    new UIProcessValidator(
      processingType,
      transform(validator),
      testCaseValidator,
      scenarioProperties,
      scenarioPropertiesConfigFinalizer,
      engineScenarioCompilationDependenciesResource,
      scenarioLabelsValidator,
      additionalValidators,
      fragmentResolver,
      stickyNotesSettings
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
      val canonical           = CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, processVersion.processName)
      val canonicalValidation = validateCanonicalProcess(canonical, processVersion, isFragment)
      // The deduplication is needed for errors that are validated on both uiValidation for DisplayableProcess and
      // CanonicalProcess validation.
      deduplicateErrors(uiValidationResult.add(canonicalValidation))
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
      .add(validateStickyNotesLength(scenarioGraph))
      .add(validateStickyNotesLimit(scenarioGraph))
      .add(validateEdgeUniqueness(scenarioGraph))
      .add(validateScenarioProperties(scenarioGraph.properties.additionalFields.properties, isFragment))
      .add(warningValidation(scenarioGraph))
  }

  def validateCanonicalProcess(
      canonical: CanonicalProcess,
      processVersion: ProcessVersion,
      isFragment: Boolean
  )(implicit loggedUser: LoggedUser): ValidationResult = {
    engineScenarioCompilationDependenciesResource
      .use { engineScenarioCompilationDependencies =>
        SyncIO {
          def validateAndFormatResult(scenario: CanonicalProcess) = {
            val jobData: JobData = JobData(scenario.metaData, processVersion)
            implicit val scenarioCompilationDependencies: ScenarioCompilationDependencies =
              new ScenarioCompilationDependencies(jobData, engineScenarioCompilationDependencies)
            val validated                 = validator.validate(scenario, isFragment)
            val nodeResults               = validated.typing.mapValuesNow(nodeInfoToResult)
            val testCasesValidationResult = validateTestCases(scenario, validated.typing)
            validated.result
              .fold(formatErrors, _ => ValidationResult.success)
              .withNodeResults(nodeResults)
              .add(testCasesValidationResult)
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
      }
      .unsafeRunSync()
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
      disabledNodes
        .map(node => (node.id, List(PrettyValidationErrors.formatErrorMessage(DisabledNode(node.id)))))
        .toMap
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
        ValidationResult.errors(
          globalErrors = errors
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
    val edgesByFrom = scenarioGraph.edges.groupBy(e => e.from)

    def findNonUniqueEdge(nodeId: NodeId, edgesFromNode: List[Edge]) = {
      val nonUniqueByType = edgesFromNode.groupBy(_.edgeType).collect {
        case (Some(eType), list) if eType.mustBeUnique && list.size > 1 =>
          PrettyValidationErrors.formatErrorMessage(NonUniqueEdgeType(eType.toString, nodeId))
      }
      val nonUniqueByTarget = edgesFromNode.groupBy(_.to).collect {
        case (to, list) if list.size > 1 =>
          PrettyValidationErrors.formatErrorMessage(NonUniqueEdge(nodeId, to.value))
      }
      (nonUniqueByType ++ nonUniqueByTarget).toList
    }

    val edgeUniquenessErrors =
      edgesByFrom.map { case (from, edges) => from -> findNonUniqueEdge(from, edges) }.filterNot(_._2.isEmpty)
    ValidationResult.errors(invalidNodes = edgeUniquenessErrors)
  }

  private def validateStickyNotesLength(scenarioGraph: ScenarioGraph): ValidationResult = {
    val tooLongStickyNotes = scenarioGraph.stickyNotes
      .filter(n => n.content.length > stickyNotesSettings.maxContentLength)

    if (tooLongStickyNotes.isEmpty) {
      ValidationResult.success
    } else {
      formatErrors(
        NonEmptyList.fromListUnsafe(
          tooLongStickyNotes.map(n =>
            StickyNoteContentTooLong(NodeId(n.id), n.content.length, stickyNotesSettings.maxContentLength)
          )
        )
      )
    }
  }

  private def validateStickyNotesLimit(scenarioGraph: ScenarioGraph): ValidationResult = {
    val numberOfStickyNotes = scenarioGraph.stickyNotes.length
    stickyNotesSettings.maxNotesCount.fold(ValidationResult.success)(notesLimit => {
      if (numberOfStickyNotes > notesLimit)
        formatErrors(
          NonEmptyList.fromListUnsafe(
            scenarioGraph.stickyNotes.map(n => StickyNotesLimitExceeded(NodeId(n.id), numberOfStickyNotes, notesLimit))
          )
        )
      else ValidationResult.success
    })
  }

  private def validateLooseNodes(scenarioGraph: ScenarioGraph): ValidationResult = {
    val looseNodesIds = scenarioGraph.nodes
      // source & fragment inputs don't have inputs
      .filterNot(n => n.isInstanceOf[FragmentInputDefinition] || n.isInstanceOf[Source])
      .filterNot(n => scenarioGraph.edges.exists(_.to == n.id))
      .map(n => n.id)

    if (looseNodesIds.isEmpty) {
      ValidationResult.success
    } else {
      formatErrors(NonEmptyList.one(LooseNode(looseNodesIds.toSet)))
    }
  }

  private def validateDuplicates(scenarioGraph: ScenarioGraph): ValidationResult = {
    val nodeIds    = scenarioGraph.nodes.map(n => n.id)
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
    val otherErrors = errors.filter { e =>
      !globalErrors.contains(e) && !propertiesErrors.contains(e)
    }
    val (nodeErrors, unclassifiedErrors) = otherErrors.partition(_.nodeIds.nonEmpty)
    val invalidNodes =
      nodeErrors
        .flatMap(error => error.nodeIds.map(nodeId => nodeId -> PrettyValidationErrors.formatErrorMessage(error)))
        .toGroupedMap

    // TODO There shouldn't be unclassified errors, but the current design of ProcessCompilationError allows for such errors
    // Try to get rid of them and make the context of the error as accurate as possible (with node ID)
    // fatal unknown errors should be presented as node errors if possible, and the errors without a node ID as global errors
    val additionalGlobalErrors =
      NonEmptyList
        .fromList(unclassifiedErrors)
        .map { errors =>
          errors.map(e => UIGlobalError(PrettyValidationErrors.formatErrorMessage(e), e.nodeIds.toList))
        }
        .fold(List.empty[UIGlobalError]) { additionalErrors =>
          logger.error(
            s"Unclassified errors occurred during scenario compilation - ${additionalErrors.toList.mkString(",")}"
          )
          additionalErrors.toList
        }

    ValidationResult.errors(
      invalidNodes = invalidNodes,
      processPropertiesErrors = propertiesErrors.map(e => PrettyValidationErrors.formatErrorMessage(e)),
      globalErrors = globalErrors.map(e =>
        UIGlobalError(PrettyValidationErrors.formatErrorMessage(e), e.nodeIds.toList)
      ) ::: additionalGlobalErrors
    )
  }

  private def validateTestCases(scenario: CanonicalProcess, nodesTyping: Map[String, NodeTypingInfo])(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): ValidationResult = {
    scenario.testCases match {
      case None =>
        ValidationResult.success
      case Some(TestCases.Single(testCase)) =>
        val testCasesNodeTyping = nodesTyping.mapValuesNow { typingInfo =>
          TestCaseValidator.NodeTyping(
            inputVariables = typingInfo.inputValidationContext.localVariables,
            outputVariables = typingInfo.outputValidationContext.map(_.localVariables).getOrElse(Map.empty),
          )
        }
        val testCasesValidationErrors =
          testCaseValidator.validateScenarioTestCases(scenario.collectAllNodes, testCasesNodeTyping, List(testCase))
        if (testCasesValidationErrors.isEmpty) {
          ValidationResult.success
        } else {
          ValidationResult.errors(testCasesValidationErrors = Some(testCasesValidationErrors))
        }
    }
  }

  private def deduplicateErrors(result: ValidationResult): ValidationResult = {
    val deduplicatedInvalidNodes = result.errors.invalidNodes.map { case (key, value) => key -> value.distinct }
    val deduplicatedProcessPropertiesErrors = result.errors.processPropertiesErrors.distinct
    val deduplicatedGlobalErrors            = result.errors.globalErrors.distinct

    result.copy(errors =
      ValidationErrors(
        invalidNodes = deduplicatedInvalidNodes,
        processPropertiesErrors = deduplicatedProcessPropertiesErrors,
        globalErrors = deduplicatedGlobalErrors,
        // Test cases are validated once, so no need to deduplicate.
        testCasesValidationErrors = result.errors.testCasesValidationErrors,
      )
    )
  }

}
