package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{invalidNel, valid}
import cats.data.ValidatedNel
import cats.implicits._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.node.NodeData

object NameValidator {

  private val nodeNameIllegalCharacters         = Set('.', '"', '\'')
  private val nodeNameIllegalCharactersReadable = "Quotation mark (\"), dot (.) and apostrophe (')"

  def validate(process: CanonicalProcess, isFragment: Boolean): ValidatedNel[ProcessCompilationError, Unit] = {
    val scenarioNameValidationResult = validateScenarioName(process.name, isFragment)
    val nodesNameValidationResult = process.nodes
      .map(node => validateNode(node.data))
      .combineAll
    val nodeNamesUniquenessValidationResult = validateUniqueNodeNames(process)

    scenarioNameValidationResult
      .combine(nodesNameValidationResult)
      .combine(nodeNamesUniquenessValidationResult)
  }

  def validateScenarioName(
      scenarioName: ProcessName,
      isFragment: Boolean
  ): ValidatedNel[ProcessCompilationError, Unit] =
    validateName(scenarioName.value).leftMap(_.map(ScenarioNameError(_, scenarioName, isFragment)))

  def validateNode(nodeData: NodeData): ValidatedNel[ProcessCompilationError, Unit] =
    validateNodeId(nodeData).combine(validateNodeName(nodeData))

  def validateNodeId(nodeData: NodeData): ValidatedNel[ProcessCompilationError, Unit] =
    validateName(nodeData.id.value).leftMap(_.map(NodeIdValidationError(_, nodeData.id)))

  def validateNodeName(nodeData: NodeData): ValidatedNel[ProcessCompilationError, Unit] =
    validateName(nodeData.name.value, nodeNameIllegalCharacters, nodeNameIllegalCharactersReadable)
      .leftMap(_.map(NodeNameValidationError(_, nodeData.name, nodeData.id)))

  private def validateName(
      id: String,
      illegalCharacters: Set[Char] = Set.empty,
      illegalCharactersReadable: String = ""
  ) = (
    validateNameIsNotEmpty(id).andThen { _ =>
      validateNameIsNotBlank(id).andThen { _ =>
        (validateNameHasNoLeadingSpaces(id), validateNameHasNoTrailingSpaces(id)).mapN((_, _) => ())
      }
    },
    validateNameHasNoIllegalCharacters(id, illegalCharacters, illegalCharactersReadable)
  )
    .mapN((_, _) => ())

  private def validateNameIsNotEmpty(id: String) =
    validate(id.isEmpty, EmptyValue)

  private def validateNameIsNotBlank(id: String) =
    validate(id.isBlank, BlankId)

  private def validateNameHasNoLeadingSpaces(id: String) =
    validate(id.startsWith(" "), LeadingSpacesId)

  private def validateNameHasNoTrailingSpaces(id: String) =
    validate(id.endsWith(" "), TrailingSpacesId)

  private def validateNameHasNoIllegalCharacters(
      id: String,
      illegalCharacters: Set[Char],
      illegalCharactersReadable: String
  ) =
    validate(id.exists(illegalCharacters.contains), IllegalCharactersId(illegalCharactersReadable))

  private def validate(condition: Boolean, error: IdErrorType) =
    if (condition) invalidNel(error) else valid(())

  private def validateUniqueNodeNames(process: CanonicalProcess): ValidatedNel[ProcessCompilationError, Unit] = {
    val duplicatedNodeNames = process.collectAllNodes
      .groupBy(_.name)
      .collect { case (name, nodes) if nodes.map(_.id).distinct.size > 1 => name -> nodes.map(_.id).toSet }
    if (duplicatedNodeNames.isEmpty) {
      valid(())
    } else {
      invalidNel(
        DuplicatedNodeNames(
          duplicatedNodeNames.keySet,
          duplicatedNodeNames.values.flatten.toSet
        )
      )
    }
  }

}
