package pl.touk.nussknacker.ui.validation

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{DisabledNode, DuplicatedNodeIds, EmptyNodeId, InvalidCharacters, LooseNode, NonUniqueEdge, NonUniqueEdgeType, ScenarioPropertiesError}
import pl.touk.nussknacker.engine.api.expression.ExpressionParser
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.{NodeTypingInfo, ProcessValidator}
import pl.touk.nussknacker.engine.graph.node.{Disableable, NodeData, Source, SubprocessInputDefinition}
import pl.touk.nussknacker.engine.util.cache.{CacheConfig, DefaultCache}
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax._
import pl.touk.nussknacker.engine.{CustomProcessValidator, ModelData}
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeTypingData, ValidationResult}
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.subprocess.SubprocessResolver

object ProcessValidation {
  def apply(modelData: ProcessingTypeDataProvider[ModelData],
            additionalProperties: ProcessingTypeDataProvider[Map[String, AdditionalPropertyConfig]],
            additionalValidators: ProcessingTypeDataProvider[List[CustomProcessValidator]],
            subprocessResolver: SubprocessResolver): ProcessValidation = {
    new ProcessValidation(modelData, additionalProperties, additionalValidators, subprocessResolver, None)
  }
}

class ProcessValidation(modelData: ProcessingTypeDataProvider[ModelData],
                        additionalPropertiesConfig: ProcessingTypeDataProvider[Map[String, AdditionalPropertyConfig]],
                        additionalValidators: ProcessingTypeDataProvider[List[CustomProcessValidator]],
                        subprocessResolver: SubprocessResolver,
                        expressionParsers: Option[PartialFunction[ExpressionParser, ExpressionParser]]) {

  /**
    * We cache there model with category as a key, because model can be reloaded.
    * In consequence of that we have to make sure that we use actual state of model
    */
  private val processValidatorCache = new DefaultCache[ValidatorKey, ProcessValidator](CacheConfig())

  import pl.touk.nussknacker.engine.util.Implicits._

  private val additionalPropertiesValidator = new AdditionalPropertiesValidator(additionalPropertiesConfig)

  def withSubprocessResolver(subprocessResolver: SubprocessResolver) = new ProcessValidation(
    modelData, additionalPropertiesConfig, additionalValidators, subprocessResolver, None
  )

  def withExpressionParsers(modify: PartialFunction[ExpressionParser, ExpressionParser]) = new ProcessValidation(
    modelData, additionalPropertiesConfig, additionalValidators, subprocessResolver, Some(modify)
  )

  def withAdditionalPropertiesConfig(additionalPropertiesConfig: ProcessingTypeDataProvider[Map[String, AdditionalPropertyConfig]]) =
    new ProcessValidation(modelData, additionalPropertiesConfig, additionalValidators, subprocessResolver, None)

  def validate(displayable: DisplayableProcess): ValidationResult = {
    val uiValidationResult = uiValidation(displayable)

    //there is no point in further validations if ui process structure is invalid
    //displayable to canonical conversion for invalid ui process structure can have unexpected results
    if (uiValidationResult.saveAllowed) {
      val canonical = ProcessConverter.fromDisplayable(displayable)
      uiValidationResult
        .add(processingTypeValidationWithTypingInfo(canonical, displayable.processingType, displayable.category))
    } else {
      uiValidationResult
    }
  }

  def processingTypeValidationWithTypingInfo(canonical: CanonicalProcess, processingType: ProcessingType, category: Category): ValidationResult = {
    (modelData.forType(processingType), additionalValidators.forType(processingType)) match {
      case (Some(model), Some(validators)) =>
        validateUsingTypeValidator(canonical, model, validators, category)
      case _ =>
        ValidationResult.errors(Map(), List(), List(PrettyValidationErrors.noValidatorKnown(processingType)))
    }
  }

  def uiValidation(displayable: DisplayableProcess): ValidationResult = {
    validateIds(displayable)
      .add(validateEmptyId(displayable))
      .add(validateDuplicates(displayable))
      .add(validateLooseNodes(displayable))
      .add(validateEdgeUniqueness(displayable))
      .add(validateAdditionalProcessProperties(displayable))
      .add(warningValidation(displayable))
  }

  private def validateUsingTypeValidator(canonical: CanonicalProcess,
                                         modelData: ModelData,
                                         additionalValidators: List[CustomProcessValidator],
                                         category: Category): ValidationResult = {
    val processValidator = processValidatorCache.getOrCreate(ValidatorKey(modelData, category)) {
      val modelCategoryValidator = modelData.prepareValidatorForCategory(Some(category))

      expressionParsers
        .map(modelCategoryValidator.withExpressionParsers)
        .getOrElse(modelCategoryValidator)
    }
    //TODO: should we validate after resolving?
    val additionalValidatorErrors = additionalValidators
      .map(_.validate(canonical))
      .sequence.fold(formatErrors, _ => ValidationResult.success)

    val resolveResult = subprocessResolver.resolveSubprocesses(canonical, category, modelData.processConfig, modelData.modelClassLoader.classLoader) match {
      case Invalid(e) => formatErrors(e)
      case _ =>
        /* 1. We remove disabled nodes from canonical to not validate disabled nodes
           2. TODO: handle types when subprocess resolution fails... */
        subprocessResolver.resolveSubprocesses(canonical.withoutDisabledNodes, category, modelData.processConfig, modelData.modelClassLoader.classLoader) match {
          case Valid(process) =>
            val validated = processValidator.validate(process)
            //FIXME: Validation errors for subprocess nodes are not properly handled by FE
            validated.result.fold(formatErrors, _ => ValidationResult.success)
              .withNodeResults(validated.typing.mapValuesNow(nodeInfoToResult))
          case Invalid(e) => formatErrors(e)
        }
    }
    resolveResult.add(additionalValidatorErrors)
  }

  private def nodeInfoToResult(typingInfo: NodeTypingInfo) = NodeTypingData(
    typingInfo.inputValidationContext.variables,
    typingInfo.parameters.map(_.map(UIProcessObjectsFactory.createUIParameter)),
    typingInfo.expressionsTypingInfo
  )

  private def warningValidation(process: DisplayableProcess): ValidationResult = {
    val disabledNodes = process.nodes.collect { case d: NodeData with Disableable if d.isDisabled.getOrElse(false) => d }
    val disabledNodesWarnings = disabledNodes.map(node => (node.id, List(PrettyValidationErrors.formatErrorMessage(DisabledNode(node.id))))).toMap
    ValidationResult.warnings(disabledNodesWarnings)
  }

  private def validateIds(displayable: DisplayableProcess): ValidationResult = {
    val invalidCharsRegexp = "[\"'\\.]".r

    ValidationResult.errors(
      displayable.nodes.map(_.id).filter(n => invalidCharsRegexp.findFirstIn(n).isDefined)
        .map(n => n -> List(PrettyValidationErrors.formatErrorMessage(InvalidCharacters(n))))
        .toMap,
      List(),
      List()
    )
  }

  private def validateAdditionalProcessProperties(displayable: DisplayableProcess): ValidationResult = {
    if (displayable.metaData.isSubprocess) {
      ValidationResult.success
    } else {
      additionalPropertiesValidator.validate(displayable)
    }
  }

  private def validateEdgeUniqueness(displayableProcess: DisplayableProcess): ValidationResult = {
    val edgesByFrom = displayableProcess.edges.groupBy(_.from)

    def findNonUniqueEdge(nodeId: String, edgesFromNode: List[Edge]) = {
      val nonUniqueByType = edgesFromNode.groupBy(_.edgeType).collect { case (Some(eType), list) if eType.mustBeUnique && list.size > 1 =>
        PrettyValidationErrors.formatErrorMessage(NonUniqueEdgeType(eType.toString, nodeId))
      }
      val nonUniqueByTarget = edgesFromNode.groupBy(_.to).collect { case (to, list) if list.size > 1 =>
        PrettyValidationErrors.formatErrorMessage(NonUniqueEdge(nodeId, to))
      }
      (nonUniqueByType ++ nonUniqueByTarget).toList
    }

    val edgeUniquenessErrors = edgesByFrom.map { case (from, edges) => from -> findNonUniqueEdge(from, edges) }.filterNot(_._2.isEmpty)
    ValidationResult.errors(edgeUniquenessErrors, List(), List())
  }


  private def validateLooseNodes(displayableProcess: DisplayableProcess): ValidationResult = {
    val looseNodes = displayableProcess.nodes
      //source & subprocess inputs don't have inputs
      .filterNot(n => n.isInstanceOf[SubprocessInputDefinition] || n.isInstanceOf[Source])
      .filterNot(n => displayableProcess.edges.exists(_.to == n.id))
      .map(n => n.id -> List(PrettyValidationErrors.formatErrorMessage(LooseNode(n.id))))
      .toMap
    ValidationResult.errors(looseNodes, List(), List())
  }

  private def validateDuplicates(displayable: DisplayableProcess): ValidationResult = {
    val nodeIds = displayable.nodes.map(_.id)
    val duplicates = nodeIds.groupBy(identity).filter(_._2.size > 1).keys.toList

    if (duplicates.isEmpty) {
      ValidationResult.success
    } else {
      ValidationResult.errors(Map(), List(), List(PrettyValidationErrors.formatErrorMessage(DuplicatedNodeIds(duplicates.toSet))))
    }
  }

  private def validateEmptyId(displayableProcess: DisplayableProcess): ValidationResult = {
    if (displayableProcess.nodes.exists(_.id.isEmpty)) {
      ValidationResult.errors(Map(), List(), List(PrettyValidationErrors.formatErrorMessage(EmptyNodeId)))
    } else {
      ValidationResult.success
    }
  }

  private def formatErrors(errors: NonEmptyList[ProcessCompilationError]): ValidationResult = {
    val processErrors = errors.filter(_.nodeIds.isEmpty)
    val (propertiesErrors, otherErrors) =  processErrors.partition(_.isInstanceOf[ScenarioPropertiesError])

    ValidationResult.errors(
      invalidNodes = (for {
        error <- errors.toList.filterNot(processErrors.contains)
        nodeId <- error.nodeIds
      } yield nodeId -> PrettyValidationErrors.formatErrorMessage(error)).toGroupedMap,
      processPropertiesErrors = propertiesErrors.map(PrettyValidationErrors.formatErrorMessage),
      globalErrors = otherErrors.map(PrettyValidationErrors.formatErrorMessage)
    )
  }

  private case class ValidatorKey(modelData: ModelData, category: Category)
}
