package pl.touk.nussknacker.ui.validation

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import cats.effect.SyncIO
import cats.effect.kernel.Resource
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.{CustomProcessValidator, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.api.{JobData, NodeId, NodeName, ProcessVersion}
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.graph.{Edge, ScenarioGraph}
import pl.touk.nussknacker.engine.api.process.{ProcessingType, ProcessName}
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, CanonicalProcessConverter}
import pl.touk.nussknacker.engine.compile.{NameValidator, NodeTypingInfo, ProcessValidator}
import pl.touk.nussknacker.engine.graph.node.{Disableable, FragmentInput, FragmentInputDefinition, NodeData, Source}
import pl.touk.nussknacker.engine.test.testcase.{TestCase, TestCases}
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
import pl.touk.nussknacker.ui.process.test.testcase
import pl.touk.nussknacker.ui.process.test.testcase.validation.TestCaseValidator
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.validation.UIProcessValidator.ValidationResultWithDetails

object UIProcessValidator {

  // See todo in ValidationResult, which could be used only on API layer and this class could contain all necessary validation results.
  final case class ValidationResultWithDetails(
      validationResult: ValidationResult,
      rawTyping: Map[String, NodeTypingInfo]
  )

}

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
  ): ValidationResult =
    validateWithDetails(scenarioGraph, processVersion, isFragment).validationResult

  def validateWithDetails(
      scenarioGraph: ScenarioGraph,
      processVersion: ProcessVersion,
      isFragment: Boolean,
  )(implicit loggedUser: LoggedUser): ValidationResultWithDetails = {
    val uiValidationResult = uiValidation(
      scenarioGraph,
      processVersion.processName,
      isFragment,
      processVersion.labels.map(ScenarioLabel.apply)
    )
    if (uiValidationResult.saveAllowed) {
      val canonical = CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, processVersion.processName)
      val ValidationResultWithDetails(canonicalValidation, rawTyping) =
        validateCanonicalProcessWithDetails(canonical, processVersion, isFragment)
      ValidationResultWithDetails(deduplicateErrors(uiValidationResult.add(canonicalValidation)), rawTyping)
    } else {
      val errorAndWarningNodeIds =
        (uiValidationResult.errors.invalidNodes.keys ++
          uiValidationResult.warnings.invalidNodes.keys).map(_.value).toSet
      val nodeNamesForErrors = scenarioGraph.nodes
        .filter(n => errorAndWarningNodeIds.contains(n.id.value))
        .map(n => n.id.value -> n.name.value)
        .toMap
      ValidationResultWithDetails(
        uiValidationResult.withNodeNames(nodeNamesForErrors),
        Map.empty[String, NodeTypingInfo]
      )
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
      .add(validateNodesIdAndName(scenarioGraph))
      .add(validateDuplicates(scenarioGraph))
      .add(validateLooseNodes(scenarioGraph))
      .add(validateStickyNotesLength(scenarioGraph))
      .add(validateStickyNotesLimit(scenarioGraph))
      .add(validateEdgeUniqueness(scenarioGraph))
      .add(validateScenarioProperties(scenarioGraph.properties.additionalFields.properties, isFragment))
      .add(
        testCaseValidator.validateTestCaseBeforeResolving(
          scenarioGraph.testCases.fold(List.empty[TestCase])(_.list.toList)
        )
      )
      .add(warningValidation(scenarioGraph))
  }

  def validateCanonicalProcess(
      canonical: CanonicalProcess,
      processVersion: ProcessVersion,
      isFragment: Boolean
  )(implicit loggedUser: LoggedUser): ValidationResult =
    validateCanonicalProcessWithDetails(canonical, processVersion, isFragment).validationResult

  private def validateCanonicalProcessWithDetails(
      canonical: CanonicalProcess,
      processVersion: ProcessVersion,
      isFragment: Boolean
  )(implicit loggedUser: LoggedUser): ValidationResultWithDetails = {
    engineScenarioCompilationDependenciesResource
      .use { engineScenarioCompilationDependencies =>
        SyncIO {
          def validateAndFormatResult(
              scenario: CanonicalProcess
          ): ValidationResultWithDetails = {
            val jobData: JobData = JobData(scenario.metaData, processVersion)
            implicit val scenarioCompilationDependencies: ScenarioCompilationDependencies =
              new ScenarioCompilationDependencies(jobData, engineScenarioCompilationDependencies)
            val validated                 = validator.validate(scenario, isFragment)
            val rawTyping                 = validated.typing
            val nodeResults               = rawTyping.mapValuesNow(nodeInfoToResult)
            val testCasesValidationResult = validateTestCases(scenario, validated.typing)
            val validationResult = validated.result
              .fold(formatErrors, _ => ValidationResult.success)
              .withNodeResults(nodeResults)
              .add(testCasesValidationResult)
            ValidationResultWithDetails(validationResult, rawTyping)
          }

          val fragments = fragmentResolver.fetchFragmentsSync(processingType)

          // TODO: should we validate after resolve?
          val additionalValidatorErrors = additionalValidators
            .map(_.validate(canonical))
            .sequence
            .fold(formatErrors, _ => ValidationResult.success)

          val resolvedScenarioResult = fragmentResolver.resolveFragments(canonical, fragments)

          // TODO: handle types when fragment resolution fails
          val ValidationResultWithDetails(validationResult, rawTyping) = resolvedScenarioResult match {
            case Invalid(fragmentResolutionErrors) =>
              ValidationResultWithDetails(formatErrors(fragmentResolutionErrors), Map.empty[String, NodeTypingInfo])
            case Valid(scenario) =>
              val ValidationResultWithDetails(validationResult, rawTyping) = validateAndFormatResult(scenario)
              val containsDisabledNodes = canonical.collectAllNodes.exists {
                case nodeData: Disableable if nodeData.isDisabled.contains(true) => true
                case _                                                           => false
              }
              if (containsDisabledNodes) {
                val resolvedScenarioWithoutDisabledNodes =
                  fragmentResolver.resolveFragments(canonical.withoutDisabledNodes, fragments)
                resolvedScenarioWithoutDisabledNodes match {
                  case Invalid(fragmentResolutionErrors) =>
                    ValidationResultWithDetails(
                      formatErrors(fragmentResolutionErrors),
                      Map.empty[String, NodeTypingInfo]
                    )
                  case Valid(scenarioWithoutDisabledNodes) =>
                    // FIXME: Validation errors for fragment nodes are not properly handled by FE
                    // We add typing data from disabled nodes to have typing and suggestions for expressions in disabled nodes
                    val ValidationResultWithDetails(resultWithoutDisabledNodes, _) =
                      validateAndFormatResult(scenarioWithoutDisabledNodes)
                    ValidationResultWithDetails(
                      resultWithoutDisabledNodes.copy(nodeResults = validationResult.nodeResults),
                      rawTyping
                    )
                }
              } else {
                ValidationResultWithDetails(validationResult, rawTyping)
              }
          }
          val finalResult = validationResult.add(additionalValidatorErrors)
          ValidationResultWithDetails(
            finalResult.withNodeNames(resolveNodeNamesForResult(finalResult, canonical, fragments)),
            rawTyping
          )
        }
      }
      .unsafeRunSync()
  }

  private def resolveNodeNamesForResult(
      result: ValidationResult,
      canonical: CanonicalProcess,
      fragments: List[CanonicalProcess]
  ): Map[String, String] = {
    val referencedNodeIds =
      (result.errors.invalidNodes.keys ++ result.warnings.invalidNodes.keys).map(_.value).toSet

    if (referencedNodeIds.isEmpty) return Map.empty

    val canonicalNodesById = canonical.collectAllNodes.map(n => n.id.value -> n).toMap

    lazy val fragmentsByName = fragments.map(f => f.name.value -> f).toMap

    referencedNodeIds.flatMap { nodeId =>
      canonicalNodesById.get(nodeId) match {
        case Some(node) => Some(nodeId -> node.name.value)
        case None =>
          for {
            outerNodeId <- canonicalNodesById.keys.find(outerNodeId => nodeId.startsWith(outerNodeId + "-"))
            outerNode = canonicalNodesById(outerNodeId)
            fi <- outerNode match {
              case fi: FragmentInput => Some(fi)
              case other =>
                logger.warn(
                  s"Node '$outerNodeId' matched by fragment prefix convention but is not a FragmentInput: $other"
                )
                None
            }
            innerNodeId = nodeId.drop(outerNodeId.length + 1)
            fragmentCanonical <- fragmentsByName.get(fi.ref.id)
            innerNode         <- fragmentCanonical.collectAllNodes.find(_.id.value == innerNodeId)
          } yield nodeId -> s"${outerNode.name.value} - ${innerNode.name.value}"
      }
    }.toMap
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
        .map(node => (node.id, List(PrettyValidationErrors.formatErrorMessage(DisabledNode(node.id, node.name)))))
        .toMap
    ValidationResult.warnings(disabledNodesWarnings)
  }

  private def validateScenarioName(processName: ProcessName, isFragment: Boolean): ValidationResult = {
    NameValidator.validateScenarioName(processName, isFragment) match {
      case Valid(_)   => ValidationResult.success
      case Invalid(e) => formatErrors(e)
    }
  }

  private def validateNodesIdAndName(scenarioGraph: ScenarioGraph): ValidationResult = {
    val nodeNameErrors = scenarioGraph.nodes
      .map(NameValidator.validateNode)
      .collect { case Invalid(e) => e }
      .reduceOption(_ concatNel _)

    nodeNameErrors match {
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
    val edgesByFrom   = scenarioGraph.edges.groupBy(e => e.from)
    val nodeNamesById = scenarioGraph.nodes.map(n => n.id -> n.name).toMap

    def findNonUniqueEdge(nodeId: NodeId, edgesFromNode: List[Edge]) = {
      val nodeName = nodeNamesById.getOrElse(nodeId, NodeName.unknown)
      val nonUniqueByType = edgesFromNode.groupBy(_.edgeType).collect {
        case (Some(eType), list) if eType.mustBeUnique && list.size > 1 =>
          PrettyValidationErrors.formatErrorMessage(NonUniqueEdgeType(eType.toString, nodeId, nodeName))
      }
      val nonUniqueByTarget = edgesFromNode.groupBy(_.to).collect {
        case (to, list) if list.size > 1 =>
          PrettyValidationErrors.formatErrorMessage(NonUniqueEdge(nodeId, nodeName, to.value))
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
    val looseNodes = scenarioGraph.nodes
      // source & fragment inputs don't have inputs
      .filterNot(n => n.isInstanceOf[FragmentInputDefinition] || n.isInstanceOf[Source])
      .filterNot(n => scenarioGraph.edges.exists(_.to == n.id))

    if (looseNodes.isEmpty) {
      ValidationResult.success
    } else {
      formatErrors(NonEmptyList.one(LooseNode(looseNodes.map(n => (n.id, n.name)).toSet)))
    }
  }

  private def validateDuplicates(scenarioGraph: ScenarioGraph): ValidationResult = {
    val duplicatedIds = scenarioGraph.nodes
      .groupBy(_.id)
      .collect { case (id, nodes) if nodes.size > 1 => id -> nodes.map(_.name).toSet }
    val duplicatedNames = scenarioGraph.nodes
      .groupBy(_.name)
      .collect { case (name, nodes) if nodes.map(_.id).distinct.size > 1 => name -> nodes.map(_.id).toSet }

    val errors =
      List(
        if (duplicatedIds.isEmpty) None else Some(DuplicatedNodeIds(duplicatedIds)),
        if (duplicatedNames.isEmpty) None
        else Some(DuplicatedNodeNames(duplicatedNames.keySet, duplicatedNames.values.flatten.toSet))
      ).flatten

    if (errors.isEmpty) {
      ValidationResult.success
    } else {
      formatErrors(NonEmptyList.fromListUnsafe(errors))
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
      case Some(TestCases(testCases)) =>
        val testCasesNodeTyping = nodesTyping.mapValuesNow { typingInfo =>
          testcase.NodeTyping(
            inputVariables = typingInfo.inputValidationContext.localVariables,
            outputVariables = typingInfo.outputValidationContext.map(_.localVariables).getOrElse(Map.empty),
          )
        }
        testCaseValidator.validateScenarioTestCases(scenario.collectAllNodes, testCasesNodeTyping, testCases.toList)
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
