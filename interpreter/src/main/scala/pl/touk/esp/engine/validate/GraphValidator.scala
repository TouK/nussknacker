package pl.touk.esp.engine.validate

import cats.data.Validated._
import cats.data.{Validated, ValidatedNel}
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.graph.node._
import pl.touk.esp.engine.traverse.NodesCollector
import pl.touk.esp.engine.validate.GraphValidationError._

object GraphValidator {

  def validate(process: EspProcess): ValidatedNel[DuplicatedNodeIds, Unit] = {
    findDuplicates(process.root).toValidatedNel
  }

  private def findDuplicates(node: Source): Validated[DuplicatedNodeIds, Unit] = {
    val allNodes = NodesCollector.collectNodes(node)
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

}

sealed trait GraphValidationError

object GraphValidationError {

  case class DuplicatedNodeIds(ids: Set[String]) extends GraphValidationError

}