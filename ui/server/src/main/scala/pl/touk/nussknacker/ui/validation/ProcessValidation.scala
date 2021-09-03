package pl.touk.nussknacker.ui.validation

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.ProcessAdditionalFields
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.expression.ExpressionParser
import pl.touk.nussknacker.engine.api.process.{AdditionalPropertyConfig, ParameterConfig}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.{NodeTypingInfo, ProcessValidator}
import pl.touk.nussknacker.engine.graph.node.{Disableable, NodeData, Source, SubprocessInputDefinition}
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.{Edge, EdgeType}
import pl.touk.nussknacker.restmodel.validation.CustomProcessValidator
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeTypingData, ValidationResult}
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.subprocess.SubprocessResolver
import shapeless.syntax.typeable._

object ProcessValidation {
  def apply(data: ProcessingTypeDataProvider[ModelData],
            additionalProperties: ProcessingTypeDataProvider[Map[String, AdditionalPropertyConfig]],
            subprocessResolver: SubprocessResolver,
            customProcessNodesValidators: ProcessingTypeDataProvider[CustomProcessValidator]): ProcessValidation = {
    new ProcessValidation(data.mapValues(_.validator), additionalProperties, subprocessResolver, customProcessNodesValidators)
  }
}

class ProcessValidation(validators: ProcessingTypeDataProvider[ProcessValidator],
                        additionalPropertiesConfig: ProcessingTypeDataProvider[Map[String, AdditionalPropertyConfig]],
                        subprocessResolver: SubprocessResolver,
                        customProcessNodesValidators: ProcessingTypeDataProvider[CustomProcessValidator]) {

  val uiValidationError = "UiValidation"

  import pl.touk.nussknacker.engine.util.Implicits._

  private val additionalPropertiesValidator = new AdditionalPropertiesValidator(additionalPropertiesConfig)

  def withSubprocessResolver(subprocessResolver: SubprocessResolver) = new ProcessValidation(validators, additionalPropertiesConfig, subprocessResolver, customProcessNodesValidators)

  def withExpressionParsers(modify: PartialFunction[ExpressionParser, ExpressionParser]) = new ProcessValidation(
    validators.mapValues(_.withExpressionParsers(modify)), additionalPropertiesConfig, subprocessResolver, customProcessNodesValidators)

  def validate(displayable: DisplayableProcess): ValidationResult = {
    val uiValidationResult = uiValidation(displayable)

    //there is no point in further validations if ui process structure is invalid
    //displayable to canonical conversion for invalid ui process structure can have unexpected results
    if (uiValidationResult.saveAllowed) {
      val canonical = ProcessConverter.fromDisplayable(displayable)
      uiValidationResult.add(processingTypeValidationWithTypingInfo(canonical, displayable.processingType))
    } else {
      uiValidationResult
    }
  }

  def processingTypeValidationWithTypingInfo(canonical: CanonicalProcess, processingType: ProcessingType): ValidationResult = {
    validators.forType(processingType) match {
      case None =>
        ValidationResult.errors(Map(), List(), List(PrettyValidationErrors.noValidatorKnown(processingType)))
      case Some(processValidator) =>
        validateUsingTypeValidator(canonical, processValidator)
    }
  }

  def uiValidation(displayable: DisplayableProcess): ValidationResult = {
    validateIds(displayable)
      .add(validateDuplicates(displayable))
      .add(validateLooseNodes(displayable))
      .add(validateEdgeUniqueness(displayable))
      .add(validateAdditionalProcessProperties(displayable))
      .add(validateWithCustomProcessValidator(displayable))
      .add(warningValidation(displayable))
  }

  private def validateUsingTypeValidator(canonical: CanonicalProcess, processValidator: ProcessValidator): ValidationResult = {
    subprocessResolver.resolveSubprocesses(canonical) match {
      case Invalid(e) => formatErrors(e)
      case _ =>
        /* 1. We remove disabled nodes from canonical to not validate disabled nodes
           2. TODO: handle types when subprocess resolution fails... */
        subprocessResolver.resolveSubprocesses(canonical.withoutDisabledNodes) match {
          case Valid(process) =>
            val validated = processValidator.validate(process)
            //FIXME: Validation errors for subprocess nodes are not properly handled by FE
            validated.result.fold(formatErrors, _ => ValidationResult.success)
              .withNodeResults(validated.typing.mapValues(nodeInfoToResult))
          case Invalid(e) => formatErrors(e)
        }
    }
  }

  private def nodeInfoToResult(typingInfo: NodeTypingInfo)
  = NodeTypingData(typingInfo.inputValidationContext.variables,
    typingInfo.parameters.map(_.map(UIProcessObjectsFactory.createUIParameter)),
    typingInfo.expressionsTypingInfo)

  private def warningValidation(process: DisplayableProcess): ValidationResult = {
    val disabledNodes = process.nodes.collect { case d: NodeData with Disableable if d.isDisabled.getOrElse(false) => d }
    val disabledNodesWarnings = disabledNodes.map(node => (node.id, List(PrettyValidationErrors.disabledNode(uiValidationError)))).toMap
    ValidationResult.warnings(disabledNodesWarnings)
  }

  private def validateIds(displayable: DisplayableProcess): ValidationResult = {
    val invalidCharsRegexp = "[\"'\\.]".r

    ValidationResult.errors(
      displayable.nodes.map(_.id).filter(n => invalidCharsRegexp.findFirstIn(n).isDefined)
        .map(n => n -> List(PrettyValidationErrors.invalidCharacters(uiValidationError))).toMap,
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

    def findNonUniqueEdge(edgesFromNode: List[Edge]) = {
      val nonUniqueByType = edgesFromNode.groupBy(_.edgeType).collect { case (Some(eType), list) if list.size > 1 =>
        PrettyValidationErrors.nonuniqeEdgeType(uiValidationError, eType)
      }
      val nonUniqueByTarget = edgesFromNode.groupBy(_.to).collect { case (to, list) if list.size > 1 =>
        PrettyValidationErrors.nonuniqeEdge(uiValidationError, to)
      }
      (nonUniqueByType ++ nonUniqueByTarget).toList
    }

    val edgeUniquenessErrors = edgesByFrom.map { case (from, edges) => from -> findNonUniqueEdge(edges) }.filterNot(_._2.isEmpty)
    ValidationResult.errors(edgeUniquenessErrors, List(), List())
  }


  private def validateLooseNodes(displayableProcess: DisplayableProcess): ValidationResult = {
    val looseNodes = displayableProcess.nodes
      //source & subprocess inputs don't have inputs
      .filterNot(n => n.isInstanceOf[SubprocessInputDefinition] || n.isInstanceOf[Source])
      .filterNot(n => displayableProcess.edges.exists(_.to == n.id))
      .map(n => n.id -> List(PrettyValidationErrors.looseNode(uiValidationError)))
      .toMap
    ValidationResult.errors(looseNodes, List(), List())
  }

  private def validateDuplicates(displayable: DisplayableProcess): ValidationResult = {
    val nodeIds = displayable.nodes.map(_.id)
    val duplicates = nodeIds.groupBy(identity).filter(_._2.size > 1).keys.toList

    if (duplicates.isEmpty) {
      ValidationResult.success
    } else {
      ValidationResult.errors(Map(), List(), List(PrettyValidationErrors.duplicatedNodeIds(uiValidationError, duplicates)))
    }

  }

  private def formatErrors(errors: NonEmptyList[ProcessCompilationError]): ValidationResult = {
    val globalErrors = errors.filter(_.nodeIds.isEmpty)
    val processPropertyErrors = errors.filter(_.nodeIds == Set(NodeTypingInfo.ExceptionHandlerNodeId))

    ValidationResult.errors(
      (for {
        error <- errors.toList.filterNot(globalErrors.contains).filterNot(processPropertyErrors.contains)
        nodeId <- error.nodeIds
      } yield nodeId -> PrettyValidationErrors.formatErrorMessage(error)).toGroupedMap,
      processPropertyErrors.map(PrettyValidationErrors.formatErrorMessage),
      globalErrors.map(PrettyValidationErrors.formatErrorMessage)
    )
  }

  private def validateWithCustomProcessValidator(process: DisplayableProcess): ValidationResult = {
    customProcessNodesValidators
      .forType(process.processingType)
      .map(_.validate(process))
      .getOrElse(ValidationResult.success)
  }
}
