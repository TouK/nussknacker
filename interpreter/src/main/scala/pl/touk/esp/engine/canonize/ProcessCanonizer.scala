package pl.touk.esp.engine.canonize

import cats.data.Validated._
import cats.std.list._
import cats.std.option._
import cats.syntax.cartesian._
import cats.syntax.traverse._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.{Semigroup, SemigroupK}
import pl.touk.esp.engine.canonicalgraph._
import pl.touk.esp.engine.canonize.ProcessUncanonizationError._
import pl.touk.esp.engine.graph._

object ProcessCanonizer {

  private implicit val nelSemigroup: Semigroup[NonEmptyList[ProcessUncanonizationError]] =
    SemigroupK[NonEmptyList].algebra[ProcessUncanonizationError]

  def canonize(process: EspProcess): CanonicalProcess = {
    CanonicalProcess(
      process.metaData,
      nodesOnTheSameLevel(process.root)
    )
  }

  private def nodesOnTheSameLevel(n: node.Node): List[canonicalnode.CanonicalNode] =
    n match {
      case node.Source(id, ref, next) =>
        canonicalnode.Source(id, ref) :: nodesOnTheSameLevel(next)
      case node.VariableBuilder(id, varName, fields, next) =>
        canonicalnode.VariableBuilder(id, varName, fields) :: nodesOnTheSameLevel(next)
      case node.Processor(id, service, next) =>
        canonicalnode.Processor(id, service) :: nodesOnTheSameLevel(next)
      case node.EndingProcessor(id, service) =>
        canonicalnode.Processor(id, service) :: Nil
      case node.Enricher(id, service, output, next) =>
        canonicalnode.Enricher(id, service, output) :: nodesOnTheSameLevel(next)
      case node.Filter(id, expression, nextTrue, nextFalse) =>
        canonicalnode.Filter(
          id, expression, nextFalse.toList.flatMap(nodesOnTheSameLevel)
        ) :: nodesOnTheSameLevel(nextTrue)
      case node.Switch(id, expression, exprVal, nexts, defaultNext) =>
        canonicalnode.Switch(
          id = id,
          expression = expression,
          exprVal = exprVal,
          nexts = nexts.map { next =>
            canonicalnode.Case(next.expression, nodesOnTheSameLevel(next.node))
          },
          defaultNext = defaultNext.toList.flatMap(nodesOnTheSameLevel)
        ) :: Nil
      case node.Sink(id, ref, endResult) =>
        canonicalnode.Sink(id, ref, endResult) :: Nil
      case node.Aggregate(id, aggregatedVar, keyExpr, duration, step, triggerExpression, foldingFunRef, next)
        => canonicalnode.Aggregate(id, aggregatedVar, keyExpr, duration, step, triggerExpression, foldingFunRef) :: nodesOnTheSameLevel(next)
    }

  def uncanonize(canonicalProcess: CanonicalProcess): ValidatedNel[ProcessUncanonizationError, EspProcess]=
    (uncanonize(canonicalProcess.nodes) andThen validateIsSource).map(EspProcess(canonicalProcess.metaData, _))

  private def validateIsSource(n: node.Node): ValidatedNel[ProcessUncanonizationError, node.Source] =
    n match {
      case source: node.Source =>
        valid(source)
      case other =>
        invalid(InvaliRootNode(other.id)).toValidatedNel
    }

  private def uncanonize(canonicalNode: List[canonicalnode.CanonicalNode]): ValidatedNel[ProcessUncanonizationError, node.Node] =
    canonicalNode match {
      case canonicalnode.Source(id, ref) :: tail =>
        uncanonize(tail).map(node.Source(id, ref, _))
      case canonicalnode.VariableBuilder(id, varName, fields) :: tail =>
        uncanonize(tail).map(node.VariableBuilder(id, varName, fields, _))
      case canonicalnode.Processor(id, ref) :: Nil =>
        valid(node.EndingProcessor(id, ref))
      case canonicalnode.Processor(id, service) :: tail =>
        uncanonize(tail).map(node.Processor(id, service, _))
      case canonicalnode.Enricher(id, service, output) :: tail =>
        uncanonize(tail).map(node.Enricher(id, service, output, _))
      case canonicalnode.Filter(id, expression, nextFalse) :: tail if nextFalse.isEmpty =>
        uncanonize(tail).map(node.Filter(id, expression, _, None))
      case canonicalnode.Filter(id, expression, nextFalse) :: tail =>
        (uncanonize(tail) |@| uncanonize(nextFalse)).map { (nextTrue, nextFalseV) =>
          node.Filter(id, expression, nextTrue, Some(nextFalseV))
        }
      case canonicalnode.Switch(id, expression, exprVal, nexts, defaultNext) :: Nil if defaultNext.isEmpty =>
        nexts.map { casee =>
          uncanonize(casee.nodes).map(node.Case(casee.expression, _))
        }.sequenceU.map(node.Switch(id, expression, exprVal, _, None))
      case canonicalnode.Switch(id, expression, exprVal, nexts, defaultNext) :: Nil =>
        val unFlattenNexts = nexts.map { casee =>
          uncanonize(casee.nodes).map(node.Case(casee.expression, _))
        }.sequenceU
        (unFlattenNexts |@| uncanonize(defaultNext)).map { (nextsV, defaultNextV) =>
          node.Switch(id, expression, exprVal, nextsV, Some(defaultNextV))
        }
      case canonicalnode.Sink(id, ref, endResult) :: Nil =>
        valid(node.Sink(id, ref, endResult))
      case canonicalnode.Aggregate(id, aggregatedVar, keyExpr, duration, slide, triggerExpression, foldingFunRef)::tail =>
        uncanonize(tail).map(node.Aggregate(id, aggregatedVar, keyExpr, duration, slide, triggerExpression, foldingFunRef, _))
      case invalidTail =>
        invalid(InvalidTailOfBranch(invalidTail.map(_.id).toSet)).toValidatedNel
    }

}

sealed trait ProcessUncanonizationError {

  def nodeIds: Set[String]

}

object ProcessUncanonizationError {

  trait InASingleNode { self: ProcessUncanonizationError =>

    override def nodeIds: Set[String] = Set(nodeId)

    protected def nodeId: String

  }

  case class InvaliRootNode(nodeId: String) extends ProcessUncanonizationError with InASingleNode

  case class InvalidTailOfBranch(nodeIds: Set[String]) extends ProcessUncanonizationError

}