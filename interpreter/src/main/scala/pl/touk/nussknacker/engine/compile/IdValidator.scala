package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{invalid, valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.node.NodeData

object IdValidator {

  import cats.implicits._

  private val nodeIdIllegalCharacters = Set('.', '"', '\'')

  def validate(process: CanonicalProcess): ValidatedNel[ProcessCompilationError, Unit] = {
    val scenarioIdValidationResult =
      validateIdIsNotEmpty(process).andThen { _ =>
        validateIdIsNotBlank(process).andThen { _ =>
          (validateIdHasNoLeadingSpaces(process), validateIdHasNoTrailingSpaces(process)).mapN((_, _) => ())
        }
      }

    val nodesIdValidationResult = process.nodes
      .map(node => validateNodeId(node.data))
      .foldLeft(Validated.validNel[ProcessCompilationError, Unit](())) { (acc, validation) =>
        acc.combine(validation)
      }

    scenarioIdValidationResult.combine(nodesIdValidationResult)
  }

  def validateNodeId(node: NodeData): ValidatedNel[ProcessCompilationError, Unit] = {
    (
      validateIdIsNotEmpty(node).andThen { _ =>
        validateIdIsNotBlank(node).andThen { _ =>
          (validateIdHasNoLeadingSpaces(node), validateIdHasNoTrailingSpaces(node)).mapN((_, _) => ())
        }
      },
      validateNodeHasNoIllegalCharacters(node)
    )
      .mapN((_, _) => ())
  }

  private def applySingleErrorValidation[T](
      predicate: T => Boolean,
      errorProducer: T => ProcessCompilationError
  )(implicit process: T): ValidatedNel[ProcessCompilationError, T] = {
    if (predicate(process))
      invalid(NonEmptyList.one(errorProducer(process)))
    else
      valid(process)
  }

  private def validateIdIsNotEmpty(implicit process: CanonicalProcess) =
    applySingleErrorValidation[CanonicalProcess](_.id.isEmpty, process => EmptyScenarioId(process.metaData.isFragment))

  private def validateIdIsNotEmpty(implicit nodeId: NodeData) =
    applySingleErrorValidation[NodeData](_.id.isEmpty, _ => EmptyNodeId())

  private def validateIdIsNotBlank(implicit process: CanonicalProcess) =
    applySingleErrorValidation[CanonicalProcess](_.id.isBlank, process => BlankScenarioId(process.metaData.isFragment))

  private def validateIdIsNotBlank(implicit node: NodeData) =
    applySingleErrorValidation[NodeData](_.id.isBlank, n => BlankNodeId(n.id))

  private def validateIdHasNoLeadingSpaces(implicit process: CanonicalProcess) =
    applySingleErrorValidation[CanonicalProcess](
      _.id.startsWith(" "),
      process => LeadingSpacesScenarioId(process.metaData.isFragment)
    )

  private def validateIdHasNoLeadingSpaces(implicit node: NodeData) =
    applySingleErrorValidation[NodeData](_.id.startsWith(" "), n => LeadingSpacesNodeId(n.id))

  private def validateIdHasNoTrailingSpaces(implicit process: CanonicalProcess) =
    applySingleErrorValidation[CanonicalProcess](
      _.id.endsWith(" "),
      process => TrailingSpacesScenarioId(process.metaData.isFragment)
    )

  private def validateIdHasNoTrailingSpaces(implicit node: NodeData) =
    applySingleErrorValidation[NodeData](_.id.endsWith(" "), n => TrailingSpacesNodeId(n.id))

  private def validateNodeHasNoIllegalCharacters(implicit node: NodeData) = {
    applySingleErrorValidation[NodeData](_.id.exists(nodeIdIllegalCharacters.contains), n => InvalidCharacters(n.id))
  }

}
