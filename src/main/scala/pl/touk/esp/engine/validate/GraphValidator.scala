package pl.touk.esp.engine.validate

import cats.data.Validated._
import cats.data.{Validated, ValidatedNel}
import pl.touk.esp.engine.graph.node._
import pl.touk.esp.engine.validate.GraphValidationError._

object GraphValidator {

  def validate(node: StartNode): ValidatedNel[DuplicatedNodeIds, Unit] = {
    findDuplicates(node).toValidatedNel
  }

  private def findDuplicates(node: StartNode): Validated[DuplicatedNodeIds, Unit] = {
    val allNodes = collectNodes(node)
    val duplicatedIds =
      allNodes.map(_.id).groupBy(identity).collect {
        case (id, grouped) if grouped.size > 1 =>
          id
      }
    if (duplicatedIds.isEmpty)
      valid(Unit)
    else
      invalid(DuplicatedNodeIds(duplicatedIds.toSet))
  }

  private def collectNodes(node: Node): List[Node] = {
    val children = node match {
      case StartNode(_, next) =>
        collectNodes(next)
      case VariableBuilder(_, _, _, next) =>
        collectNodes(next)
      case Processor(_, _, next) =>
        collectNodes(next)
      case Enricher(_, _, _, next) =>
        collectNodes(next)
      case Filter(_, _, nextTrue, nextFalse) =>
        collectNodes(nextTrue) ::: nextFalse.toList.flatMap(collectNodes)
      case Switch(_, _, _, nexts, _) =>
        nexts.flatMap {
          case (_, n) => collectNodes(n)
        }
      case End(_, _) =>
        Nil
    }
    node :: children
  }

}

sealed trait GraphValidationError

object GraphValidationError {

  case class DuplicatedNodeIds(ids: Set[String]) extends GraphValidationError

}