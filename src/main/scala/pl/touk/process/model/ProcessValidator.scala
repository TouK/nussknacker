package pl.touk.process.model

import cats.data.{Validated, ValidatedNel}
import cats.data.Validated._
import pl.touk.process.model.ProcessVerificationError._
import pl.touk.process.model.graph.node._

object ProcessValidator {

  def validate(node: Node): ValidatedNel[DuplicatedNodeIds, Unit] = {
    findDuplicates(node).toValidatedNel
  }

  private def findDuplicates(node: Node): Validated[DuplicatedNodeIds, Unit] = {
    val allNodes = collectNodes(node)
    val duplicatedIds =
      allNodes.map(_.metaData.id).groupBy(identity).collect {
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

sealed trait ProcessVerificationError

object ProcessVerificationError {

  case class DuplicatedNodeIds(ids: Set[String]) extends ProcessVerificationError

}