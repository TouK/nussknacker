package pl.touk.esp.engine.marshall

import cats.data.Validated._
import cats.std.list._
import cats.std.option._
import cats.syntax.cartesian._
import cats.syntax.traverse._
import cats.data.ValidatedNel
import pl.touk.esp.engine.graph._
import pl.touk.esp.engine.marshall.GraphUnFlattenError.{InvaliRootNode, InvalidTailOfBranch}
import pl.touk.esp.engine.flatgraph._

object GraphFlattener {

  def flatten(process: EspProcess): FlatProcess = {
    FlatProcess(
      process.metaData,
      nodesOnTheSameLevel(process.root)
    )
  }

  private def nodesOnTheSameLevel(n: node.Node): List[flatnode.FlatNode] =
    n match {
      case node.Source(id, ref, next) =>
        flatnode.Source(id, ref) :: nodesOnTheSameLevel(next)
      case node.VariableBuilder(id, varName, fields, next) =>
        flatnode.VariableBuilder(id, varName, fields) :: nodesOnTheSameLevel(next)
      case node.Processor(id, service, next) =>
        flatnode.Processor(id, service) :: nodesOnTheSameLevel(next)
      case node.Enricher(id, service, output, next) =>
        flatnode.Enricher(id, service, output) :: nodesOnTheSameLevel(next)
      case node.Filter(id, expression, nextTrue, nextFalse) =>
        flatnode.Filter(
          id, expression, nextFalse.toList.flatMap(nodesOnTheSameLevel)
        ) :: nodesOnTheSameLevel(nextTrue)
      case node.Switch(id, expression, exprVal, nexts, defaultNext) =>
        flatnode.Switch(
          id = id,
          expression = expression,
          exprVal = exprVal,
          nexts = nexts.map { next =>
            flatnode.Case(next.expression, nodesOnTheSameLevel(next.node))
          },
          defaultNext = defaultNext.toList.flatMap(nodesOnTheSameLevel)
        ) :: Nil
      case node.Sink(id, ref, endResult) =>
        flatnode.Sink(id, ref, endResult) :: Nil
    }

  def unFlatten(flatProcess: FlatProcess): ValidatedNel[GraphUnFlattenError, EspProcess]=
    (unFlatten(flatProcess.nodes) andThen validateIsSource).map(EspProcess(flatProcess.metaData, _))

  private def validateIsSource(n: node.Node): ValidatedNel[GraphUnFlattenError, node.Source] =
    n match {
      case source: node.Source =>
        valid(source)
      case other =>
        invalid(InvaliRootNode(other.id)).toValidatedNel
    }

  private def unFlatten(flatNode: List[flatnode.FlatNode]): ValidatedNel[GraphUnFlattenError, node.Node] =
    flatNode match {
      case flatnode.Source(id, ref) :: tail =>
        unFlatten(tail).map(node.Source(id, ref, _))
      case flatnode.VariableBuilder(id, varName, fields) :: tail =>
        unFlatten(tail).map(node.VariableBuilder(id, varName, fields, _))
      case flatnode.Processor(id, service) :: tail =>
        unFlatten(tail).map(node.Processor(id, service, _))
      case flatnode.Enricher(id, service, output) :: tail =>
        unFlatten(tail).map(node.Enricher(id, service, output, _))
      case flatnode.Filter(id, expression, nextFalse) :: tail if nextFalse.isEmpty =>
        unFlatten(tail).map(node.Filter(id, expression, _, None))
      case flatnode.Filter(id, expression, nextFalse) :: tail =>
        (unFlatten(tail) |@| unFlatten(nextFalse)).map { (nextTrue, nextFalseV) =>
          node.Filter(id, expression, nextTrue, Some(nextFalseV))
        }
      case flatnode.Switch(id, expression, exprVal, nexts, defaultNext) :: Nil if defaultNext.isEmpty =>
        nexts.map { casee =>
          unFlatten(casee.nodes).map(node.Case(casee.expression, _))
        }.sequenceU.map(node.Switch(id, expression, exprVal, _, None))
      case flatnode.Switch(id, expression, exprVal, nexts, defaultNext) :: Nil =>
        val unFlattenNexts = nexts.map { casee =>
          unFlatten(casee.nodes).map(node.Case(casee.expression, _))
        }.sequenceU
        (unFlattenNexts |@| unFlatten(defaultNext)).map { (nextsV, defaultNextV) =>
          node.Switch(id, expression, exprVal, nextsV, Some(defaultNextV))
        }
      case flatnode.Sink(id, ref, endResult) :: Nil =>
        valid(node.Sink(id, ref, endResult))
      case invalidTail =>
        invalid(InvalidTailOfBranch(invalidTail.map(_.id).toSet)).toValidatedNel
    }

}

sealed trait GraphUnFlattenError {

  def nodeIds: Set[String]

}

object GraphUnFlattenError {

  trait InASingleNode { self: GraphUnFlattenError =>

    override def nodeIds: Set[String] = Set(nodeId)

    protected def nodeId: String

  }

  case class InvaliRootNode(nodeId: String) extends GraphUnFlattenError with InASingleNode

  case class InvalidTailOfBranch(nodeIds: Set[String]) extends GraphUnFlattenError

}