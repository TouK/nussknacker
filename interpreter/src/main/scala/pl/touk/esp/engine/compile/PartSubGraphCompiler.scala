package pl.touk.esp.engine.compile

import cats.data.Validated._
import cats.data.ValidatedNel
import cats.instances.list._
import cats.instances.option._
import pl.touk.esp.engine._
import pl.touk.esp.engine.compile.ProcessCompilationError._
import pl.touk.esp.engine.compile.dumb._
import pl.touk.esp.engine.compiledgraph.expression.ExpressionParser
import pl.touk.esp.engine.compiledgraph.node.CustomNode
import pl.touk.esp.engine.compiledgraph.evaluatedparam.Parameter
import pl.touk.esp.engine.definition.DefinitionExtractor._
import pl.touk.esp.engine.definition._
import pl.touk.esp.engine.spel.SpelExpressionParser
import pl.touk.esp.engine.splittedgraph._

class PartSubGraphCompiler(protected val expressionParsers: Map[String, ExpressionParser],
                           protected val services: Map[String, ObjectWithMethodDef]) extends PartSubGraphCompilerBase {

  override type ParametersProviderT = ObjectWithMethodDef

  override def compile(n: splittednode.SplittedNode): ValidatedNel[PartSubGraphCompilationError, compiledgraph.node.Node] = {
    super.compile(n)
  }

  override protected def createServiceInvoker(obj: ObjectWithMethodDef) =
    ServiceInvoker(obj)

}

class PartSubGraphValidator(protected val expressionParsers: Map[String, ExpressionParser],
                            protected val services: Map[String, ObjectDefinition]) extends PartSubGraphCompilerBase {

  override type ParametersProviderT = ObjectDefinition

  override protected def createServiceInvoker(obj: ObjectDefinition) =
    DumbServiceInvoker

}

private[compile] trait PartSubGraphCompilerBase {

  type ParametersProviderT <: ParametersProvider

  protected def expressionParsers: Map[String, ExpressionParser]
  protected def services: Map[String, ParametersProviderT]

  private val syntax = ValidatedSyntax[PartSubGraphCompilationError]
  import syntax._

  def validate(n: splittednode.SplittedNode): ValidatedNel[PartSubGraphCompilationError, Unit] = {
    compile(n).map(_ => Unit)
  }

  def compile(n: splittednode.SplittedNode): ValidatedNel[PartSubGraphCompilationError, compiledgraph.node.Node] = {
    implicit val nodeId = NodeId(n.id)
    n match {
      case s: splittednode.Source =>
        compile(s)
      case splittednode.VariableBuilder(id, varName, fields, next) =>
        A.map2(fields.map(compile).sequence, compile(next))(compiledgraph.node.VariableBuilder(id, varName, _, _))
      case splittednode.Processor(id, ref, next) =>
        A.map2(compile(ref), compile(next))(compiledgraph.node.Processor(id, _, _))
      case splittednode.EndingProcessor(id, ref) =>
        compile(ref).map(compiledgraph.node.EndingProcessor(id, _))
      case splittednode.Enricher(id, ref, outName, next) =>
        A.map2(compile(ref), compile(next))(compiledgraph.node.Enricher(id, _, outName, _))
      case splittednode.Filter(id, expression, nextTrue, nextFalse) =>
        A.map3(compile(expression), compile(nextTrue), nextFalse.map(compile).sequence)(compiledgraph.node.Filter(id, _, _, _))
      case splittednode.Switch(id, expression, exprVal, nexts, defaultNext) =>
        A.map3(compile(expression), nexts.map(compile).sequence, defaultNext.map(compile).sequence)(compiledgraph.node.Switch(id, _, exprVal, _, _))
      case splittednode.Aggregate(id, keyExpression, triggerExpression, next) =>
        A.map3(compile(keyExpression), triggerExpression.map(compile).sequence, compile(next))(compiledgraph.node.Aggregate(id, _, _, _))
      case splittednode.CustomNode(id, customNodeRef, parameters, next) =>
        val validParams = parameters.map(compile).sequence
        A.map2(validParams, compile(next))(compiledgraph.node.CustomNode(id, _, _))
      case splittednode.Sink(id, optionalExpression) =>
        optionalExpression.map(compile).sequence.map(compiledgraph.node.Sink(id, _))
    }
  }

  def compile(s: splittednode.Source): ValidatedNel[PartSubGraphCompilationError, compiledgraph.node.Source] =
    compile(s.next).map(compiledgraph.node.Source(s.id, _))

  private def compile(next: splittednode.Next): ValidatedNel[PartSubGraphCompilationError, compiledgraph.node.Next] = {
    next match {
      case splittednode.NextNode(n) => compile(n).map(compiledgraph.node.NextNode)
      case splittednode.PartRef(ref) => valid(compiledgraph.node.PartRef(ref))
    }
  }

  private def compile(n: graph.service.ServiceRef)
                     (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, compiledgraph.service.ServiceRef] = {
    val validService = services.get(n.id).map(valid).getOrElse(invalid(MissingService(n.id))).toValidatedNel
    val validParams = n.parameters.map(compile).sequence
    A.map2(validService, validParams)((_, _)).andThen {
      case (obj: ParametersProviderT@unchecked, params: List[Parameter]) =>
        validateServiceParameters(obj, params.map(_.name)).map { _ =>
          val invoker = createServiceInvoker(obj)
          compiledgraph.service.ServiceRef(n.id, invoker, params)
        }
    }
  }

  protected def createServiceInvoker(obj: ParametersProviderT): ServiceInvoker

  private def validateServiceParameters(parameterProvider: ParametersProviderT, usedParamNames: List[String])
                                       (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, Unit] = {
    val definedParamNames = parameterProvider.parameters.map(_.name).toSet
    val usedParamNamesSet = usedParamNames.toSet
    val redundantParams = usedParamNamesSet.diff(definedParamNames)
    if (redundantParams.nonEmpty) invalid(RedundantParameters(redundantParams)).toValidatedNel else valid(Unit)
  }

  private def compile(n: graph.variable.Field)
                     (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, compiledgraph.variable.Field] =
    compile(n.expression).map(compiledgraph.variable.Field(n.name, _))

  private def compile(n: graph.evaluatedparam.Parameter)
                     (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, compiledgraph.evaluatedparam.Parameter] =
    compile(n.expression).map(compiledgraph.evaluatedparam.Parameter(n.name, _))

  private def compile(n: splittednode.Case)
                     (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, compiledgraph.node.Case] =
    A.map2(compile(n.expression), compile(n.node))(compiledgraph.node.Case)

  private def compile(n: graph.expression.Expression)
                     (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, compiledgraph.expression.Expression] = {
    val validParser = expressionParsers
      .get(n.language)
      .map(valid)
      .getOrElse(invalid(NotSupportedExpressionLanguage(n.language)))
    (validParser andThen { parser =>
      parser.parse(n.expression).leftMap(err => ExpressionParseError(err.message, n.expression))
    }).toValidatedNel
  }

}

object PartSubGraphCompilerBase {

  private[compile] val defaultParsers = {
    val parsersSeq = Seq(SpelExpressionParser.default)
    parsersSeq.map(p => p.languageId -> p).toMap
  }

}

object PartSubGraphCompiler {

  def default(servicesDefs: Map[String, ObjectWithMethodDef]): PartSubGraphCompiler = {
    new PartSubGraphCompiler(PartSubGraphCompilerBase.defaultParsers, servicesDefs)
  }
}

object PartSubGraphValidator {

  def default(services: Map[String, ObjectDefinition]) = {
    new PartSubGraphValidator(PartSubGraphCompilerBase.defaultParsers, services)
  }

}