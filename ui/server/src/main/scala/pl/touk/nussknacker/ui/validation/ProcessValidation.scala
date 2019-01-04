package pl.touk.nussknacker.ui.validation

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.compile.{ProcessCompilationError, ProcessValidator}
import pl.touk.nussknacker.engine.graph.node.{Disableable, NodeData, Source, SubprocessInputDefinition}
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.ui.api.AdditionalProcessProperty
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.ProcessAdditionalFields
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.subprocess.SubprocessResolver
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import shapeless.syntax.typeable._

object ProcessValidation{

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  def apply(data: Map[ProcessingType, ModelData], subprocessResolver: SubprocessResolver) : ProcessValidation = {
    val additionalFields = data.mapValues(_.processConfig.getOrElse[Map[String, AdditionalProcessProperty]]("additionalFieldsConfig", Map.empty))
    new ProcessValidation(data.mapValues(_.validator), additionalFields, subprocessResolver)
  }
}

class ProcessValidation(validators: Map[ProcessingType, ProcessValidator],
                        additionalFieldsConfig: Map[ProcessingType, Map[String, AdditionalProcessProperty]],
                        subprocessResolver: SubprocessResolver) {

  val uiValidationError = "UiValidation"

  import pl.touk.nussknacker.ui.util.CollectionsEnrichments._

  def withSubprocessResolver(subprocessResolver: SubprocessResolver) = new ProcessValidation(validators, additionalFieldsConfig, subprocessResolver)

  def toValidated(displayableProcess: DisplayableProcess): ValidatedDisplayableProcess = {
    new ValidatedDisplayableProcess(displayableProcess, validate(displayableProcess))
  }

  def validate(displayable: DisplayableProcess): ValidationResult = {
    val uiValidationResult = uiValidation(displayable)
      .add(warningValidation(displayable))

    //there is no point in further validations if ui process structure is invalid
    //displayable to canonical conversion for invalid ui process structure can have unexpected results
    if (uiValidationResult.saveAllowed) {
      uiValidationResult.add(processingTypeValidation(displayable))
    } else {
      uiValidationResult
    }
  }

  private def processingTypeValidation(displayable: DisplayableProcess) = {
    val processingType = displayable.processingType
    validators.get(processingType) match {
      case None =>
        ValidationResult.errors(Map(), List(), List(PrettyValidationErrors.noValidatorKnown(processingType)))
      case Some(processValidator) =>
        validateUsingTypeValidator(displayable, processValidator)
    }
  }

  private def validateUsingTypeValidator(displayable: DisplayableProcess, processValidator: ProcessValidator): ValidationResult = {
    val canonical = ProcessConverter.fromDisplayable(displayable)
    //TODO: handle types when subprocess resolution fails...
    subprocessResolver.resolveSubprocesses(canonical) match {
      case Valid(process) =>
        val validated = processValidator.validate(process)
        validated.result.fold(formatErrors, _ => ValidationResult.success).withTypes(validated.variablesInNodes)
      case Invalid(pce) => formatErrors(pce)
    }
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
      .add(validateAdditionalProcessProperties(displayable))
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

  private def validateAdditionalProcessProperties(displayable: DisplayableProcess): ValidationResult = {
    additionalFieldsConfig.get(displayable.processingType) match {
      case None =>
        ValidationResult.errors(Map(), List(), List(PrettyValidationErrors.noValidatorKnown(displayable.processingType)))
      case Some(propertiesConfig) =>
        val nonEmptyFields = displayable.metaData
          .additionalFields.flatMap(_.cast[ProcessAdditionalFields]).toList.flatMap(_.properties.filterNot(_._2.isEmpty).keys)
        val errors = propertiesConfig
          .filter(_._2.isRequired)
          .filterNot(field => nonEmptyFields.contains(field._1))
          .map(field => PrettyValidationErrors.emptyRequiredField(uiValidationError, field._1, field._2.label))
          .toList
        ValidationResult.errors(Map(), errors, List())

    }
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
    val groupIds = displayable.metaData.additionalFields
      .flatMap(a => a.cast[ProcessAdditionalFields])
      .toList
      .flatMap(_.groups)
      .map(_.id)
    val nodeIds = displayable.nodes.map(_.id)

    //in theory it would be possible to have group named like one of nodes inside, but it's not worth complicating logic...  
    val duplicates = (groupIds ++ nodeIds).groupBy(identity).filter(_._2.size > 1).keys.toList

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