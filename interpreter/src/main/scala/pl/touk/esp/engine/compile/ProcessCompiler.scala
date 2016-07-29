package pl.touk.esp.engine.compile

import cats.data.Validated._
import cats.std.list._
import cats.std.option._
import cats.syntax.cartesian._
import cats.syntax.traverse._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine._
import pl.touk.esp.engine.compiledgraph._
import pl.touk.esp.engine.traverse.NodesCollector
import ProcessCompilationError._
import cats.{Semigroup, SemigroupK}
import pl.touk.esp.engine.compile.ProcessCompiler.NodeId
import pl.touk.esp.engine.compiledgraph.expression.ExpressionParser
import pl.touk.esp.engine.spel.SpelExpressionParser

class ProcessCompiler(expressionParsers: Map[String, ExpressionParser]) {

  private implicit val nelMonoid: Semigroup[NonEmptyList[ProcessCompilationError]] =
    SemigroupK[NonEmptyList].algebra[ProcessCompilationError]

  def compile(process: EspProcess): ValidatedNel[ProcessCompilationError, CompiledProcess] = {
    (findDuplicates(process.root).toValidatedNel |@| compile(process.root)).map { (_, root) =>
      CompiledProcess(process.metaData, root)
    }
  }

  private def findDuplicates(node: graph.node.Source): Validated[ProcessCompilationError, Unit] = {
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

  private def compile(s: graph.node.Source): ValidatedNel[ProcessCompilationError, compiledgraph.node.Source] =
    compile(s.next).map(compiledgraph.node.Source(s.id, s.ref, _))

  private def compile(n: graph.node.Node): ValidatedNel[ProcessCompilationError, compiledgraph.node.Node] = {
    implicit val nodeId = NodeId(n.id)
    n match {
      case s: graph.node.Source =>
        compile(s)
      case graph.node.VariableBuilder(id, varName, fields, next) =>
        (fields.map(compile).sequenceU |@| compile(next))
          .map(compiledgraph.node.VariableBuilder(id, varName, _, _))
      case graph.node.Processor(id, ref, next) =>
        (compile(ref) |@| compile(next))
          .map(compiledgraph.node.Processor(id, _, _))
      case graph.node.Enricher(id, ref, outName, next) =>
        (compile(ref) |@| compile(next))
          .map(compiledgraph.node.Enricher(id, _, outName, _))
      case graph.node.Filter(id, expression, nextTrue, nextFalse) =>
        (compile(expression) |@| compile(nextTrue) |@| nextFalse.map(compile).sequenceU)
          .map(compiledgraph.node.Filter(id, _, _, _))
      case graph.node.Switch(id, expression, exprVal, nexts, defaultNext) =>
        (compile(expression) |@| nexts.map(compile).sequenceU |@| defaultNext.map(compile).sequenceU)
          .map(compiledgraph.node.Switch(id, _, exprVal, _, _))
      case graph.node.Sink(id, ref, optionalExpression) =>
        optionalExpression.map(compile).sequenceU.map(compiledgraph.node.Sink(id, ref, _))
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

  private def compile(n: graph.node.Case)
                     (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, compiledgraph.node.Case] =
    (compile(n.expression) |@| compile(n.node)).map(compiledgraph.node.Case)

  private def compile(n: graph.expression.Expression)
                     (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, compiledgraph.expression.Expression] = {
    val validParser = expressionParsers
      .get(n.language)
      .map(valid)
      .getOrElse(invalid(NotSupportedExpressionLanguage(n.language)))
    (validParser andThen { parser =>
      parser.parse(n.expression).leftMap(err => ExpressionParseError(err.message))
    }).toValidatedNel
  }

}

object ProcessCompiler {

  val default = ProcessCompiler(SpelExpressionParser)

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

  case class ExpressionParseError(message: String, nodeId: String)
    extends ProcessCompilationError with InASingleNode

  object ExpressionParseError {
    def apply(message: String)(implicit nodeId: NodeId): ExpressionParseError =
      ExpressionParseError(message, nodeId.id)
  }

}