package pl.touk.esp.ui.validation

import cats.data.NonEmptyList
import pl.touk.esp.engine.compile.{ProcessCompilationError, ProcessValidator}
import pl.touk.esp.engine.graph.node.{Disableable, NodeData, Source, SubprocessInputDefinition}
import pl.touk.esp.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.process.marshall.ProcessConverter
import pl.touk.esp.ui.process.subprocess.SubprocessResolver
import pl.touk.esp.ui.validation.ValidationResults.ValidationResult

class ProcessValidation(validators: Map[ProcessingType, ProcessValidator], subprocessResolver: SubprocessResolver) {

  val uiValidationError = "UiValidation"

  import pl.touk.esp.ui.util.CollectionsEnrichments._

  def validate(displayable: DisplayableProcess): ValidationResult = {
    val processValidator = validators(displayable.processingType)
    val canonical = ProcessConverter.fromDisplayable(displayable)
    val compilationValidationResult =
      subprocessResolver.resolveSubprocesses(canonical).andThen(processValidator.validate)
        .leftMap(formatErrors).swap.getOrElse(ValidationResult.success)
    val uiValidationResult = uiValidation(displayable)
    val validationWarningsResult = warningValidation(displayable)
    compilationValidationResult
      .add(uiValidationResult)
      .add(validationWarningsResult)
  }

  private def warningValidation(process: DisplayableProcess): ValidationResult = {
    val disabledNodes = process.nodes.collect { case d: NodeData with Disableable if d.isDisabled.getOrElse(false) => d }
    val disabledNodesWarnings = disabledNodes.map(node => (node.id, List(PrettyValidationErrors.disabledNode(uiValidationError)))).toMap
    ValidationResult.warnings(disabledNodesWarnings)
  }

  private def uiValidation(displayable: DisplayableProcess): ValidationResult = {
    validateIds(displayable)
      .add(validateDuplicates(displayable))
      .add(validateLooseNodes(displayable))
      .add(validateDuplicateSource(displayable))
      .add(validateEdgeUniqueness(displayable))
  }

  private def validateIds(displayable: DisplayableProcess): ValidationResult = {
    val invalidCharsRegexp = "[\"']".r

    ValidationResult.errors(
      displayable.nodes.map(_.id).filter(n => invalidCharsRegexp.findFirstIn(n).isDefined)
        .map(n => n -> List(PrettyValidationErrors.invalidCharacters(uiValidationError))).toMap,
      List(),
      List()
    )
  }


  private def validateEdgeUniqueness(displayableProcess: DisplayableProcess): ValidationResult = {
    val edgeUniquenessErrors = displayableProcess.edges
      .groupBy(_.from).map { case (from, edges) =>
      from -> edges.groupBy(_.edgeType).collect { case (Some(eType), list) if list.size > 1 =>
        PrettyValidationErrors.nonuniqeEdge(uiValidationError, eType)
      }.toList
    }.filterNot(_._2.isEmpty)

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

  private def validateDuplicateSource(displayableProcess: DisplayableProcess):ValidationResult = {
    val inputs = displayableProcess.nodes
      .filter(n => n.isInstanceOf[SubprocessInputDefinition] || n.isInstanceOf[Source])
      .map(_.id)
    if (inputs.size > 1) {
      ValidationResult.errors(Map(), List(), List(PrettyValidationErrors.tooManySources(uiValidationError, inputs)))
    } else {
      ValidationResult.success
    }

  }

  private def validateDuplicates(displayable: DisplayableProcess): ValidationResult = {
    val duplicates = displayable.nodes.groupBy(_.id).filter(_._2.size > 1).keys.toList
    if (duplicates.isEmpty) {
      ValidationResult.success
    } else {
      ValidationResult.errors(Map(), List(), List(PrettyValidationErrors.duplicatedNodeIds(uiValidationError, duplicates)))
    }

  }

  private def formatErrors(errors: NonEmptyList[ProcessCompilationError]): ValidationResult = {
    val globalErrors = errors.filter(_.nodeIds.isEmpty)
    val processPropertyErrors = errors.filter(_.nodeIds == Set(ProcessCompilationError.ProcessNodeId))

    ValidationResult.errors(
      (for {
        error <- errors.toList.filterNot(globalErrors.contains).filterNot(processPropertyErrors.contains)
        nodeId <- error.nodeIds
      } yield nodeId -> PrettyValidationErrors.formatErrorMessage(error)).flatGroupByKey,
      processPropertyErrors.map(PrettyValidationErrors.formatErrorMessage),
      globalErrors.map(PrettyValidationErrors.formatErrorMessage)
    )
  }

}