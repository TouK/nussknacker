package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{invalid, valid}
import cats.data.{NonEmptyList, ValidatedNel}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

object IdValidator {

  import cats.implicits._

  private val nodeIdIllegalCharacters = Set('.', '"', '\'')

  def validate(process: CanonicalProcess): ValidatedNel[ProcessCompilationError, Unit] = {
    val scenarioIdValidationResult = validateScenarioId(process.id, process.metaData.isFragment)
    val nodesIdValidationResult = process.nodes
      .map(node => validateNodeId(node.data.id))
      .combineAll

    scenarioIdValidationResult.combine(nodesIdValidationResult)
  }

  def validateScenarioId(scenarioId: String, isFragment: Boolean): ValidatedNel[ProcessCompilationError, Unit] = {
    val data = ScenarioIdValidationData(scenarioId, isFragment)
    validateIdIsNotEmpty(data).andThen { _ =>
      validateIdIsNotBlank(data).andThen { _ =>
        (validateIdHasNoLeadingSpaces(data), validateIdHasNoTrailingSpaces(data)).mapN((_, _) => ())
      }
    }
  }

  def validateNodeId(nodeId: String): ValidatedNel[ProcessCompilationError, Unit] = {
    (
      validateIdIsNotEmpty(nodeId).andThen { _ =>
        validateIdIsNotBlank(nodeId).andThen { _ =>
          (validateIdHasNoLeadingSpaces(nodeId), validateIdHasNoTrailingSpaces(nodeId)).mapN((_, _) => ())
        }
      },
      validateNodeHasNoIllegalCharacters(nodeId)
    )
      .mapN((_, _) => ())
  }

  private final case class ScenarioIdValidationData(scenarioId: String, isFragment: Boolean)

  private def validateIdIsNotEmpty(implicit process: ScenarioIdValidationData) =
    applySingleErrorValidation[ScenarioIdValidationData](
      process.scenarioId.isEmpty,
      EmptyScenarioId(process.isFragment)
    )

  private def validateIdIsNotEmpty(implicit nodeId: String) =
    applySingleErrorValidation[String](nodeId.isEmpty, EmptyNodeId())

  private def validateIdIsNotBlank(implicit process: ScenarioIdValidationData) =
    applySingleErrorValidation[ScenarioIdValidationData](
      process.scenarioId.isBlank,
      BlankScenarioId(process.isFragment)
    )

  private def validateIdIsNotBlank(implicit nodeId: String) =
    applySingleErrorValidation[String](nodeId.isBlank, BlankNodeId(nodeId))

  private def validateIdHasNoLeadingSpaces(implicit process: ScenarioIdValidationData) =
    applySingleErrorValidation[ScenarioIdValidationData](
      process.scenarioId.startsWith(" "),
      LeadingSpacesScenarioId(process.isFragment)
    )

  private def validateIdHasNoLeadingSpaces(implicit nodeId: String) =
    applySingleErrorValidation[String](nodeId.startsWith(" "), LeadingSpacesNodeId(nodeId))

  private def validateIdHasNoTrailingSpaces(implicit process: ScenarioIdValidationData) =
    applySingleErrorValidation[ScenarioIdValidationData](
      process.scenarioId.endsWith(" "),
      TrailingSpacesScenarioId(process.isFragment)
    )

  private def validateIdHasNoTrailingSpaces(implicit nodeId: String) =
    applySingleErrorValidation[String](nodeId.endsWith(" "), TrailingSpacesNodeId(nodeId))

  private def validateNodeHasNoIllegalCharacters(implicit nodeId: String) = {
    applySingleErrorValidation[String](
      nodeId.exists(nodeIdIllegalCharacters.contains),
      InvalidCharactersNodeId(nodeId)
    )
  }

  private def applySingleErrorValidation[T](
      isValid: Boolean,
      error: ProcessCompilationError
  )(implicit validated: T): ValidatedNel[ProcessCompilationError, T] = {
    if (isValid)
      invalid(NonEmptyList.one(error))
    else
      valid(validated)
  }

}
