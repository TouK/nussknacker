package pl.touk.esp.engine.compile

import cats.data.Validated._
import cats.std.list._
import cats.std.option._
import cats.syntax.cartesian._
import cats.syntax.traverse._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.{Semigroup, SemigroupK}
import pl.touk.esp.engine._
import ProcessCompilationError._
import pl.touk.esp.engine.compile.ProcessCompiler.NodeId
import pl.touk.esp.engine.compiledgraph.expression.{Expression, ExpressionParser}
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.spel.SpelExpressionParser
import pl.touk.esp.engine.split.{NodesCollector, PartsCollector, ProcessSplitter}
import pl.touk.esp.engine.splittedgraph._
import pl.touk.esp.engine.splittedgraph.part._

class ProcessCompiler(expressionParsers: Map[String, ExpressionParser]) {

  private implicit val nelSemigroup: Semigroup[NonEmptyList[ProcessCompilationError]] =
    SemigroupK[NonEmptyList].algebra[ProcessCompilationError]

  def validate(process: EspProcess): ValidatedNel[ProcessCompilationError, Unit] = {
    val splittedProcess = ProcessSplitter.split(process)
    validate(splittedProcess)
  }

  def validate(process: SplittedProcess): ValidatedNel[ProcessCompilationError, Unit] = {
    (findDuplicates(process.source).toValidatedNel |@| validateAllParts(process)).map { (_, _) =>
      Unit
    }
  }

  private def findDuplicates(part: SourcePart): Validated[ProcessCompilationError, Unit] = {
    val allNodes = NodesCollector.collectNodesInAllParts(part)
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

  private def validateAllParts(process: SplittedProcess): ValidatedNel[ProcessCompilationError, Unit] = {
    PartsCollector.collectParts(process.source).map(validatePart).sequenceU.map(_ => Unit)
  }

  private def validatePart(part: ProcessPart): ValidatedNel[ProcessCompilationError, Unit] = {
    val validated = part match {
      case source: SourcePart =>
        compile(source.source)
      case agg: AggregatePart =>
        compile(agg.aggregate)
      case sink: SinkPart =>
        compile(sink.sink)
    }
    validated.map(_ => Unit)
  }

  def compile(n: splittednode.SplittedNode): ValidatedNel[ProcessCompilationError, compiledgraph.node.Node] = {
    implicit val nodeId = NodeId(n.id)
    n match {
      case s: splittednode.Source =>
        compile(s)
      case splittednode.VariableBuilder(id, varName, fields, next) =>
        (fields.map(compile).sequenceU |@| compile(next))
          .map(compiledgraph.node.VariableBuilder(id, varName, _, _))
      case splittednode.Processor(id, ref, next) =>
        (compile(ref) |@| compile(next))
          .map(compiledgraph.node.Processor(id, _, _))
      case splittednode.EndingProcessor(id, ref) =>
        compile(ref).map(compiledgraph.node.EndingProcessor(id, _))
      case splittednode.Enricher(id, ref, outName, next) =>
        (compile(ref) |@| compile(next))
          .map(compiledgraph.node.Enricher(id, _, outName, _))
      case splittednode.Filter(id, expression, nextTrue, nextFalse) =>
        (compile(expression) |@| compile(nextTrue) |@| nextFalse.map(compile).sequenceU)
          .map(compiledgraph.node.Filter(id, _, _, _))
      case splittednode.Switch(id, expression, exprVal, nexts, defaultNext) =>
        (compile(expression) |@| nexts.map(compile).sequenceU |@| defaultNext.map(compile).sequenceU)
          .map(compiledgraph.node.Switch(id, _, exprVal, _, _))
      case splittednode.Aggregate(id, keyExpression, triggerExpression, next) =>
        (compile(keyExpression) |@| triggerExpression.map(compile).sequenceU |@| compile(next))
          .map(compiledgraph.node.Aggregate(id, _, _, _))
      case splittednode.Sink(id, optionalExpression) =>
        optionalExpression.map(compile).sequenceU.map(compiledgraph.node.Sink(id, _))
    }
  }

  def compile(s: splittednode.Source): ValidatedNel[ProcessCompilationError, compiledgraph.node.Source] =
    compile(s.next).map(compiledgraph.node.Source(s.id, _))

  private def compile(next: splittednode.Next): ValidatedNel[ProcessCompilationError, compiledgraph.node.Next] = {
    next match {
      case splittednode.NextNode(n) => compile(n).map(compiledgraph.node.NextNode)
      case splittednode.PartRef(ref) => valid(compiledgraph.node.PartRef(ref))
    }
  }

  private def compile(n: graph.service.ServiceRef)
                     (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, compiledgraph.service.ServiceRef] =
    n.parameters.map(compile).sequenceU.map(compiledgraph.service.ServiceRef(n.id, _))

  private def compile(n: graph.variable.Field)
                     (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, compiledgraph.variable.Field] =
    compile(n.expression).map(compiledgraph.variable.Field(n.name, _))

  private def compile(n: graph.service.Parameter)
                     (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, compiledgraph.service.Parameter] =
    compile(n.expression).map(compiledgraph.service.Parameter(n.name, _))

  private def compile(n: splittednode.Case)
                     (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, compiledgraph.node.Case] =
    (compile(n.expression) |@| compile(n.node)).map(compiledgraph.node.Case)

  private def compile(n: graph.expression.Expression)
                     (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, compiledgraph.expression.Expression] = {
    val validParser = expressionParsers
      .get(n.language)
      .map(valid)
      .getOrElse(invalid(NotSupportedExpressionLanguage(n.language)))
    (validParser andThen { parser =>
      parser.parse(n.expression).leftMap(err => ExpressionParseError(err.message, n.expression))
    }).toValidatedNel
  }

}

object ProcessCompiler {

  val default = ProcessCompiler(SpelExpressionParser.default)

  def apply(parsers: ExpressionParser*) =
    new ProcessCompiler(parsers.map(p => p.languageId -> p).toMap)

  case class NodeId(id: String)

}

sealed trait ProcessCompilationError {
  def nodeIds: Set[String]
}

object ProcessCompilationError {

  trait InASingleNode { self: ProcessCompilationError =>

    override def nodeIds: Set[String] = Set(nodeId)

    protected def nodeId: String

  }

  case class DuplicatedNodeIds(nodeIds: Set[String]) extends ProcessCompilationError

  case class NotSupportedExpressionLanguage(languageId: String, nodeId: String)
    extends ProcessCompilationError with InASingleNode

  object NotSupportedExpressionLanguage {
    def apply(languageId: String)(implicit nodeId: NodeId): NotSupportedExpressionLanguage =
      NotSupportedExpressionLanguage(languageId, nodeId.id)
  }

  case class ExpressionParseError(message: String, nodeId: String, originalExpr: String)
    extends ProcessCompilationError with InASingleNode

  object ExpressionParseError {
    def apply(message: String, originalExpr: String)(implicit nodeId: NodeId): ExpressionParseError =
      ExpressionParseError(message, nodeId.id, originalExpr)
  }

}