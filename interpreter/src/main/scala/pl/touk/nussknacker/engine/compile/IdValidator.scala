package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{invalidNel, valid}
import cats.data.ValidatedNel
import cats.implicits._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

object IdValidator {

  private val nodeIdIllegalCharacters         = Set('.', '"', '\'')
  private val nodeIdIllegalCharactersReadable = "Quotation mark (\"), dot (.) and apostrophe (')"

  def validate(process: CanonicalProcess, isFragment: Boolean): ValidatedNel[ProcessCompilationError, Unit] = {
    val scenarioIdValidationResult = validateScenarioName(process.name, isFragment)
    val nodesIdValidationResult = process.nodes
      .map(node => validateNodeId(node.data.id))
      .combineAll

    scenarioIdValidationResult.combine(nodesIdValidationResult)
  }

  def validateScenarioName(
      scenarioName: ProcessName,
      isFragment: Boolean
  ): ValidatedNel[ProcessCompilationError, Unit] =
    validateId(scenarioName.value).leftMap(_.map(ScenarioNameError(_, scenarioName, isFragment)))

  def validateNodeId(nodeId: String): ValidatedNel[ProcessCompilationError, Unit] =
    validateId(nodeId, nodeIdIllegalCharacters, nodeIdIllegalCharactersReadable)
      .leftMap(_.map(NodeIdValidationError(_, nodeId)))

  private def validateId(
      id: String,
      illegalCharacters: Set[Char] = Set.empty,
      nodeIdIllegalCharactersReadable: String = ""
  ) =
    (
      validateIdIsNotEmpty(id).andThen { _ =>
        validateIdIsNotBlank(id).andThen { _ =>
          (validateIdHasNoLeadingSpaces(id), validateIdHasNoTrailingSpaces(id)).mapN((_, _) => ())
        }
      },
      validateIdHasNoIllegalCharacters(id, illegalCharacters, nodeIdIllegalCharactersReadable)
    )
      .mapN((_, _) => ())

  private def validateIdIsNotEmpty(id: String) =
    validate(id.isEmpty, EmptyValue)

  private def validateIdIsNotBlank(id: String) =
    validate(id.isBlank, BlankId)

  private def validateIdHasNoLeadingSpaces(id: String) =
    validate(id.startsWith(" "), LeadingSpacesId)

  private def validateIdHasNoTrailingSpaces(id: String) =
    validate(id.endsWith(" "), TrailingSpacesId)

  private def validateIdHasNoIllegalCharacters(
      id: String,
      illegalCharacters: Set[Char],
      illegalCharactersReadable: String
  ) =
    validate(id.exists(illegalCharacters.contains), IllegalCharactersId(illegalCharactersReadable))

  private def validate(condition: Boolean, error: IdErrorType) =
    if (condition) invalidNel(error) else valid(())

}
