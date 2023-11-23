package pl.touk.nussknacker.ui.validation

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.engine.api.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.engine.api.expression.ExpressionParser
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.{IdValidator, NodeTypingInfo, ProcessValidator}
import pl.touk.nussknacker.engine.graph.node.{Disableable, FragmentInputDefinition, NodeData, Source}
import pl.touk.nussknacker.engine.util.cache.{CacheConfig, DefaultCache}
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax._
import pl.touk.nussknacker.engine.{CustomProcessValidator, ModelData}
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeTypingData, ValidationErrors, ValidationResult}
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.fragment.FragmentResolver
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider

object ProcessValidation {

  def apply(
      modelData: ProcessingTypeDataProvider[ModelData, _],
      scenarioProperties: ProcessingTypeDataProvider[Map[String, ScenarioPropertyConfig], _],
      additionalValidators: ProcessingTypeDataProvider[List[CustomProcessValidator], _],
      fragmentResolver: FragmentResolver
  ): ProcessValidation = {
    new ProcessValidation(modelData, scenarioProperties, additionalValidators, fragmentResolver, None)
  }

}

class ProcessValidation(
    modelData: ProcessingTypeDataProvider[ModelData, _],
    scenarioPropertiesConfig: ProcessingTypeDataProvider[Map[String, ScenarioPropertyConfig], _],
    additionalValidators: ProcessingTypeDataProvider[List[CustomProcessValidator], _],
    fragmentResolver: FragmentResolver,
    expressionParsers: Option[PartialFunction[ExpressionParser, ExpressionParser]]
) {

  /**
   * We cache there model with category as a key, because model can be reloaded.
   * In consequence of that we have to make sure that we use actual state of model
   */
  private val processValidatorCache = new DefaultCache[ValidatorKey, ProcessValidator](CacheConfig())

  import pl.touk.nussknacker.engine.util.Implicits._

  private val scenarioPropertiesValidator = new ScenarioPropertiesValidator(scenarioPropertiesConfig)

  def withFragmentResolver(fragmentResolver: FragmentResolver) = new ProcessValidation(
    modelData,
    scenarioPropertiesConfig,
    additionalValidators,
    fragmentResolver,
    None
  )

  def withExpressionParsers(modify: PartialFunction[ExpressionParser, ExpressionParser]) = new ProcessValidation(
    modelData,
    scenarioPropertiesConfig,
    additionalValidators,
    fragmentResolver,
    Some(modify)
  )

  def withScenarioPropertiesConfig(
      scenarioPropertiesConfig: ProcessingTypeDataProvider[Map[String, ScenarioPropertyConfig], _]
  ) =
    new ProcessValidation(modelData, scenarioPropertiesConfig, additionalValidators, fragmentResolver, None)

  def validate(displayable: DisplayableProcess): ValidationResult = {
    val uiValidationResult = uiValidation(displayable)

    // there is no point in further validations if ui process structure is invalid
    // displayable to canonical conversion for invalid ui process structure can have unexpected results
    if (uiValidationResult.saveAllowed) {
      val canonical = ProcessConverter.fromDisplayable(displayable)
      // The deduplication is needed for errors that are validated on both uiValidation for DisplayableProcess and
      // CanonicalProcess validation.
      deduplicateErrors(
        uiValidationResult
          .add(processingTypeValidationWithTypingInfo(canonical, displayable.processingType, displayable.category))
      )
    } else {
      uiValidationResult
    }
  }

  def processingTypeValidationWithTypingInfo(
      canonical: CanonicalProcess,
      processingType: ProcessingType,
      category: Category
  ): ValidationResult = {
    (modelData.forType(processingType), additionalValidators.forType(processingType)) match {
      case (Some(model), Some(validators)) =>
        validateUsingTypeValidator(canonical, model, validators, category)
      case _ =>
        ValidationResult.errors(Map(), List(), List(PrettyValidationErrors.noValidatorKnown(processingType)))
    }
  }

  // Some of these validations are duplicated with CanonicalProcess validations in order to show them in case when there
  // is an error preventing graph canonization. For example we want to display node and scenario id errors for scenarios
  // that have loose nodes. If you want to achieve this result, you need to add these validations here and deduplicate
  // resulting errors later.
  def uiValidation(displayable: DisplayableProcess): ValidationResult = {
    validateScenarioId(displayable)
      .add(validateNodesId(displayable))
      .add(validateDuplicates(displayable))
      .add(validateLooseNodes(displayable))
      .add(validateEdgeUniqueness(displayable))
      .add(validateScenarioProperties(displayable))
      .add(warningValidation(displayable))
  }

  private def validateUsingTypeValidator(
      canonical: CanonicalProcess,
      modelData: ModelData,
      additionalValidators: List[CustomProcessValidator],
      category: Category
  ): ValidationResult = {
    val processValidator = processValidatorCache.getOrCreate(ValidatorKey(modelData, category)) {
      val modelCategoryValidator = ProcessValidator.default(modelData, Some(category))

      expressionParsers
        .map(modelCategoryValidator.withExpressionParsers)
        .getOrElse(modelCategoryValidator)
    }
    // TODO: should we validate after resolving?
    val additionalValidatorErrors = additionalValidators
      .map(_.validate(canonical))
      .sequence
      .fold(formatErrors, _ => ValidationResult.success)

    val resolveResult = fragmentResolver.resolveFragments(canonical, category) match {
      case Invalid(e) => formatErrors(e)
      case _          =>
        /* 1. We remove disabled nodes from canonical to not validate disabled nodes
           2. TODO: handle types when fragment resolution fails... */
        fragmentResolver.resolveFragments(canonical.withoutDisabledNodes, category) match {
          case Valid(process) =>
            val validated = processValidator.validate(process)
            // FIXME: Validation errors for fragment nodes are not properly handled by FE
            validated.result
              .fold(formatErrors, _ => ValidationResult.success)
              .withNodeResults(validated.typing.mapValuesNow(nodeInfoToResult))
          case Invalid(e) => formatErrors(e)
        }
    }
    resolveResult.add(additionalValidatorErrors)
  }

  private def nodeInfoToResult(typingInfo: NodeTypingInfo) = NodeTypingData(
    typingInfo.inputValidationContext.localVariables,
    typingInfo.parameters.map(_.map(UIProcessObjectsFactory.createUIParameter)),
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

  private def validateScenarioId(displayable: DisplayableProcess): ValidationResult = {
    IdValidator.validateScenarioId(displayable.id, displayable.metaData.isFragment) match {
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

  private def validateScenarioProperties(displayable: DisplayableProcess): ValidationResult = {
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
