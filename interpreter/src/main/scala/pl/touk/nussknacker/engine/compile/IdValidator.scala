package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{invalidNel, valid}
import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import cats.implicits._

object IdValidator {

  private val nodeIdIllegalCharacters = Set('.', '"', '\'')

  def validate(process: CanonicalProcess): ValidatedNel[ProcessCompilationError, Unit] = {
    val scenarioIdValidationResult = validateScenarioId(process.id, process.metaData.isFragment)
    val nodesIdValidationResult = process.nodes
      .map(node => validateNodeId(node.data.id))
      .combineAll

    scenarioIdValidationResult.combine(nodesIdValidationResult)
  }

  def validateScenarioId(scenarioId: String, isFragment: Boolean): ValidatedNel[ProcessCompilationError, Unit] =
    validateIdWithMapping(scenarioId, Set.empty, e => toScenarioIdError(e, isFragment))

  def validateNodeId(nodeId: String): ValidatedNel[ProcessCompilationError, Unit] =
    validateIdWithMapping(nodeId, nodeIdIllegalCharacters, toNodeError)

  private def validateIdWithMapping(
      nodeId: String,
      illegalCharacters: Set[Char],
      errorMapping: IdValidationError => ProcessCompilationError
  ): ValidatedNel[ProcessCompilationError, Unit] =
    validateId(nodeId, illegalCharacters).leftMap(_.map(errorMapping))

  private def validateId(id: String, illegalCharacters: Set[Char]) = {
    (
      validateIdIsNotEmpty(id).andThen { _ =>
        validateIdIsNotBlank(id).andThen { _ =>
          (validateIdHasNoLeadingSpaces(id), validateIdHasNoTrailingSpaces(id)).mapN((_, _) => ())
        }
      },
      validateIdHasNoIllegalCharacters(id, illegalCharacters)
    )
      .mapN((_, _) => ())
  }

  private def validateIdIsNotEmpty(id: String) = {
    if (id.isEmpty) invalidNel(EmptyIdError()) else valid(())
  }

  private def validateIdIsNotBlank(id: String) = {
    if (id.trim.isEmpty) invalidNel(BlankIdError(id)) else valid(())
  }

  private def validateIdHasNoLeadingSpaces(id: String) = {
    if (id.startsWith(" ")) invalidNel(LeadingSpacesIdError(id)) else valid(())
  }

  private def validateIdHasNoTrailingSpaces(id: String) = {
    if (id.endsWith(" ")) invalidNel(TrailingSpacesIdError(id)) else valid(())
  }

  private def validateIdHasNoIllegalCharacters(id: String, illegalCharacters: Set[Char]) = {
    if (id.exists(illegalCharacters.contains)) invalidNel(IllegalCharactersIdError(id)) else valid(())
  }

  private def toNodeError(idError: IdValidationError): ProcessCompilationError = idError match {
    case EmptyIdError()               => EmptyNodeId()
    case BlankIdError(id)             => BlankNodeId(id)
    case LeadingSpacesIdError(id)     => LeadingSpacesNodeId(id)
    case TrailingSpacesIdError(id)    => TrailingSpacesNodeId(id)
    case IllegalCharactersIdError(id) => InvalidCharactersNodeId(id)
  }

  private def toScenarioIdError(idError: IdValidationError, isFragment: Boolean): ProcessCompilationError =
    idError match {
      case EmptyIdError()           => EmptyScenarioId(isFragment)
      case BlankIdError(_)          => BlankScenarioId(isFragment)
      case LeadingSpacesIdError(_)  => LeadingSpacesScenarioId(isFragment)
      case TrailingSpacesIdError(_) => TrailingSpacesScenarioId(isFragment)
      case IllegalCharactersIdError(_) =>
        ScenarioNameValidationError("Scenario name contains invalid characters", "Invalid characters in scenario name")
    }

  private sealed trait IdValidationError

  private final case class EmptyIdError()                       extends IdValidationError
  private final case class BlankIdError(id: String)             extends IdValidationError
  private final case class LeadingSpacesIdError(id: String)     extends IdValidationError
  private final case class TrailingSpacesIdError(id: String)    extends IdValidationError
  private final case class IllegalCharactersIdError(id: String) extends IdValidationError

}
