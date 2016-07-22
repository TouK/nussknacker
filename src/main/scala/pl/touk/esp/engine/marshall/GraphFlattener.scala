package pl.touk.esp.engine.marshall

import cats.data.Xor
import pl.touk.esp.engine.graph._
import pl.touk.esp.engine.marshall.GraphFlattenerError.UnsupportedConstruction
import pl.touk.esp.engine.flatgraph._

object GraphFlattener {

  def flatten(n: node.Node): List[flatnode.FlatNode] = {
    nodesOnTheSameLevel(n)
  }

  private def nodesOnTheSameLevel(n: node.Node): List[flatnode.FlatNode] =
    n match {
      case node.StartNode(id, next) =>
        flatnode.Start(id) :: nodesOnTheSameLevel(next)
      case node.VariableBuilder(id, varName, fields, next) =>
        flatnode.VariableBuilder(id, varName, fields) :: nodesOnTheSameLevel(next)
      case node.Processor(id, service, next) =>
        flatnode.Processor(id, service) :: nodesOnTheSameLevel(next)
      case node.Enricher(id, service, output, next) =>
        flatnode.Enricher(id, service, output) :: nodesOnTheSameLevel(next)
      case node.Filter(id, expression, nextTrue, nextFalse) =>
        flatnode.Filter(
          id, expression, nextFalse.toList.flatMap(flatten)
        ) :: nodesOnTheSameLevel(nextTrue)
      case node.Switch(id, expression, exprVal, nexts, defaultNext) =>
        flatnode.Switch(
          id = id,
          expression = expression,
          exprVal = exprVal,
          nexts = nexts.map { next =>
            flatnode.Case(next.expression, flatten(next.node))
          },
          defaultNext = defaultNext.toList.flatMap(flatten)
        ) :: Nil
      case node.End(id, endResult) =>
        flatnode.End(id, endResult) :: Nil
    }

  def unFlatten(flatNode: List[flatnode.FlatNode]): Xor[GraphFlattenerError, node.Node] =
    flatNode match {
      case flatnode.Start(id) :: tail =>
        for {
          next <- unFlatten(tail)
        } yield node.StartNode(id, next)
      case flatnode.VariableBuilder(id, varName, fields) :: tail =>
        for {
          next <- unFlatten(tail)
        } yield node.VariableBuilder(id, varName, fields, next)
      case flatnode.Processor(id, service) :: tail =>
        for {
          next <- unFlatten(tail)
        } yield node.Processor(id, service, next)
      case flatnode.Enricher(id, service, output) :: tail =>
        for {
          next <- unFlatten(tail)
        } yield node.Enricher(id, service, output, next)
      case flatnode.Filter(id, expression, nextFalse) :: tail if nextFalse.isEmpty =>
        for {
          nextTrue <- unFlatten(tail)
        } yield node.Filter(id, expression, nextTrue, None)
      case flatnode.Filter(id, expression, nextFalse) :: tail =>
        for {
          nextTrue <- unFlatten(tail)
          nextFalseR <- unFlatten(nextFalse.toList)
        } yield node.Filter(id, expression, nextTrue, Some(nextFalseR))
      case flatnode.Switch(id, expression, exprVal, nexts, defaultNext) :: Nil if defaultNext.isEmpty =>
        for {
          nextsR <- {
            nexts.foldRight(Xor.right[GraphFlattenerError, List[node.Case]](List.empty)) {
              case (casee, acc) =>
                for {
                  accR <- acc
                  next <- unFlatten(casee.nodes.toList)
                } yield node.Case(casee.expression, next) :: accR
            }
          }
        } yield node.Switch(id, expression, exprVal, nextsR, None)
      case flatnode.Switch(id, expression, exprVal, nexts, defaultNext) :: Nil =>
        for {
          nextsR <- {
            nexts.foldRight(Xor.right[GraphFlattenerError, List[node.Case]](List.empty)) {
              case (casee, acc) =>
                for {
                  accR <- acc
                  next <- unFlatten(casee.nodes.toList)
                } yield node.Case(casee.expression, next) :: accR
            }
          }
          defaultNextR <- unFlatten(defaultNext.toList)
        } yield node.Switch(id, expression, exprVal, nextsR, Some(defaultNextR))
      case flatnode.End(id, endResult) :: Nil =>
        Xor.right(node.End(id, endResult))
      case unexpected =>
        Xor.left(UnsupportedConstruction(unexpected))
    }

}

sealed trait GraphFlattenerError

object GraphFlattenerError {
  case class UnsupportedConstruction(unexpected: List[flatnode.FlatNode]) extends GraphFlattenerError
}