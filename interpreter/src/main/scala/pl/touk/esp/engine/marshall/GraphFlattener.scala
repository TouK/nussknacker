package pl.touk.esp.engine.marshall

import cats.data.Xor
import pl.touk.esp.engine.graph._
import pl.touk.esp.engine.marshall.GraphFlattenerError.{InvaliRootNode, InvalidTailOfBranch}
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

  def unFlatten(flatProcess: FlatProcess): Xor[GraphFlattenerError, EspProcess] =
    for {
      root <- unFlatten(flatProcess.nodes)
      source <- {
        root match {
          case source: node.Source =>
            Xor.right(source)
          case other =>
            Xor.left(InvaliRootNode(other))
        }
      }
    } yield EspProcess(flatProcess.metaData, source)

  private def unFlatten(flatNode: List[flatnode.FlatNode]): Xor[GraphFlattenerError, node.Node] =
    flatNode match {
      case flatnode.Source(id, ref) :: tail =>
        for {
          next <- unFlatten(tail)
        } yield node.Source(id, ref, next)
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
      case flatnode.Sink(id, ref, endResult) :: Nil =>
        Xor.right(node.Sink(id, ref, endResult))
      case invalidTail =>
        Xor.left(InvalidTailOfBranch(invalidTail))
    }

}

sealed trait GraphFlattenerError

object GraphFlattenerError {

  case class InvaliRootNode(start: node.Node) extends GraphFlattenerError

  case class InvalidTailOfBranch(unexpected: List[flatnode.FlatNode]) extends GraphFlattenerError

}