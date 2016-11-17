package pl.touk.esp.engine.compile

import cats.data.Validated._
import cats.data.ValidatedNel
import cats.instances.list._
import cats.instances.option._
import pl.touk.esp.engine._
import pl.touk.esp.engine.compile.PartSubGraphCompilerBase.{CompiledNode, NextWithContext}
import pl.touk.esp.engine.compile.ProcessCompilationError._
import pl.touk.esp.engine.compile.dumb._
import pl.touk.esp.engine.compiledgraph.evaluatedparam.Parameter
import pl.touk.esp.engine.compiledgraph.expression.ExpressionParser
import pl.touk.esp.engine.definition.DefinitionExtractor._
import pl.touk.esp.engine.definition._
import pl.touk.esp.engine.graph.node.{EndingNodeData, OneOutputSubsequentNodeData}
import pl.touk.esp.engine.spel.SpelExpressionParser
import pl.touk.esp.engine.splittedgraph._
import pl.touk.esp.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.esp.engine.types.EspTypeUtils

class PartSubGraphCompiler(protected val expressionParsers: Map[String, ExpressionParser],
                           protected val services: Map[String, ObjectWithMethodDef]) extends PartSubGraphCompilerBase {

  override type ParametersProviderT = ObjectWithMethodDef

  //robimy dostep publiczny
  override def compile(n: splittednode.SplittedNode[_]): ValidatedNel[PartSubGraphCompilationError, CompiledNode] = {
    super.compile(n)
  }

  //robimy dostep publiczny
  override def compile(n: splittednode.SplittedNode[_], ctx: ValidationContext): ValidatedNel[PartSubGraphCompilationError, CompiledNode] = {
    super.compile(n, ctx)
  }

  override def compileWithoutContextValidation(n: SplittedNode[_]): ValidatedNel[PartSubGraphCompilationError, CompiledNode] = {
    super.compileWithoutContextValidation(n)
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

  type ParametersProviderT <: ObjectMetadata

  protected def expressionParsers: Map[String, ExpressionParser]
  protected def services: Map[String, ParametersProviderT]

  private val syntax = ValidatedSyntax[PartSubGraphCompilationError]
  import syntax._

  def validate(n: splittednode.SplittedNode[_], ctx: ValidationContext): ValidatedNel[PartSubGraphCompilationError, ValidationContext] = {
    compile(n, ctx).map(_.ctx)
  }

  def validateWithoutContextValidation(n: splittednode.SplittedNode[_]): ValidatedNel[PartSubGraphCompilationError, ValidationContext] = {
    compileWithoutContextValidation(n).map(_.ctx)
  }

  protected def compile(n: splittednode.SplittedNode[_]): ValidatedNel[PartSubGraphCompilationError, CompiledNode] = {
    new Compiler(true).doCompile(n, ValidationContext())
  }

  protected def compile(n: splittednode.SplittedNode[_], ctx: ValidationContext): ValidatedNel[PartSubGraphCompilationError, CompiledNode] = {
    new Compiler(true).doCompile(n, ctx)
  }

  protected def compileWithoutContextValidation(n: splittednode.SplittedNode[_]): ValidatedNel[PartSubGraphCompilationError, CompiledNode] = {
    new Compiler(false).doCompile(n, ValidationContext())
  }

  //TODO: no to wyglada troche strasznie, nie??
  private class Compiler(contextValidationEnabled: Boolean) {
    def doCompile(n: SplittedNode[_], ctx: ValidationContext): ValidatedNel[PartSubGraphCompilationError, CompiledNode] = {
      implicit val nodeId = NodeId(n.id)
      n match {
        case splittednode.SourceNode(graph.node.Source(id, ref), next) =>
          compile(next, ctx).map(nwc => CompiledNode(compiledgraph.node.Source(id, nwc.next), nwc.ctx))
        case splittednode.OneOutputSubsequentNode(data: OneOutputSubsequentNodeData, next) =>
          data match {
            case graph.node.VariableBuilder(id, varName, fields) =>
              val newCtx = ctx.withVariable(varName, ClazzRef(classOf[Map[String, Any]])) //jak wyciagnac informacie o typach zmiennych w mapie?
              A.map2(fields.map(f => compile(f, newCtx)).sequence, compile(next, newCtx))(
                (fields, nextWithCtx) =>
                  CompiledNode(compiledgraph.node.VariableBuilder(id, varName, fields, nextWithCtx.next), nextWithCtx.ctx))
            case graph.node.Processor(id, ref) =>
              A.map2(compile(ref, ctx), compile(next, ctx))((ref, nextWithCtx) =>
                CompiledNode(compiledgraph.node.Processor(id, ref, nextWithCtx.next), nextWithCtx.ctx))
            case graph.node.Enricher(id, ref, outName) =>
              val newCtx = services.get(ref.id).map {
                case o: ObjectWithMethodDef =>
                  EspTypeUtils.getGenericMethodType(o.method) match {
                    case Some(futureGenericType) => ctx.withVariable(outName, ClazzRef(futureGenericType))
                    case None => ctx
                  }
                case o: ObjectDefinition =>
                  o.returnType.map(ctx.withVariable(outName, _)).getOrElse(ctx)
              }.getOrElse(ctx)
              A.map2(compile(ref, newCtx), compile(next, newCtx))((ref, nextWithCtx) =>
                CompiledNode(compiledgraph.node.Enricher(id, ref, outName, nextWithCtx.next), nextWithCtx.ctx))
            case graph.node.CustomNode(id, outputVar, customNodeRef, parameters) =>
              val newCtx = ctx.copy(variables = Map(outputVar -> ClazzRef(classOf[Any]))) //na razie olewamy
              val validParams = parameters.map(p => compile(p, newCtx)).sequence
              A.map2(validParams, compile(next, newCtx))((params, nextWithCtx) =>
                CompiledNode(compiledgraph.node.CustomNode(id, params, nextWithCtx.next), nextWithCtx.ctx))
          }
        case splittednode.FilterNode(graph.node.Filter(id, expression), nextTrue, nextFalse) =>
          A.map3(compile(expression, ctx), compile(nextTrue, ctx), nextFalse.map(next => compile(next, ctx)).sequence)(
            (expr, nextWithCtx, nextWithCtxFalse) => CompiledNode(compiledgraph.node.Filter(id, expr, nextWithCtx.next,
              nextWithCtxFalse.map(_.next)), nextWithCtxFalse.map(nwc => ValidationContext.merge(nwc.ctx, nextWithCtx.ctx)).getOrElse(nextWithCtx.ctx)))
        case splittednode.SwitchNode(graph.node.Switch(id, expression, exprVal), nexts, defaultNext) =>
          val newCtx = ctx.withVariable(exprVal, ClazzRef(classOf[Any]))
          A.map3(compile(expression, newCtx), nexts.map(n => compile(n, newCtx)).sequence, defaultNext.map(dn => compile(dn, newCtx)).sequence)(
            (expr, cases, nextWithCtx) => {
              val defaultCtx = nextWithCtx.map(_.ctx).getOrElse(ctx)
              CompiledNode(compiledgraph.node.Switch(id, expr, exprVal, cases.unzip._1, nextWithCtx.map(_.next)),
                ValidationContext.merge(defaultCtx, cases.unzip._2.reduce(ValidationContext.merge)))
            })
        case splittednode.EndingNode(data: EndingNodeData) =>
          data match {
            case graph.node.Processor(id, ref) =>
              compile(ref, ctx).map(compiledgraph.node.EndingProcessor(id, _)).map(CompiledNode(_, ctx))
            case graph.node.Sink(id, _, optionalExpression) =>
              optionalExpression.map(oe => compile(oe, ctx)).sequence.map(compiledgraph.node.Sink(id, _)).map(CompiledNode(_, ctx))
          }
      }
    }

    private def compile(next: splittednode.Next, ctx: ValidationContext): ValidatedNel[PartSubGraphCompilationError, NextWithContext] = {
      next match {
        case splittednode.NextNode(n) => doCompile(n, ctx).map(cn => NextWithContext(compiledgraph.node.NextNode(cn.node), cn.ctx))
        case splittednode.PartRef(ref) => valid(NextWithContext(compiledgraph.node.PartRef(ref), ctx))
      }
    }

    private def compile(n: graph.service.ServiceRef, ctx: ValidationContext)
                       (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, compiledgraph.service.ServiceRef] = {
      val validService = services.get(n.id).map(valid).getOrElse(invalid(MissingService(n.id))).toValidatedNel
      val validParams = n.parameters.map(p => compile(p, ctx)).sequence
      A.map2(validService, validParams)((_, _)).andThen {
        case (obj: ParametersProviderT@unchecked, params: List[Parameter]) =>
          validateServiceParameters(obj, params.map(_.name)).map { _ =>
            val invoker = createServiceInvoker(obj)
            compiledgraph.service.ServiceRef(n.id, invoker, params)
          }
      }
    }
    private def validateServiceParameters(parameterProvider: ParametersProviderT, usedParamNames: List[String])
                                         (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, Unit] = {
      val definedParamNames = parameterProvider.parameters.map(_.name).toSet
      val usedParamNamesSet = usedParamNames.toSet
      val redundantParams = usedParamNamesSet.diff(definedParamNames)
      if (redundantParams.nonEmpty) invalid(RedundantParameters(redundantParams)).toValidatedNel else valid(Unit)
    }

    private def compile(n: graph.variable.Field, ctx: ValidationContext)
                       (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, compiledgraph.variable.Field] =
      compile(n.expression, ctx).map(compiledgraph.variable.Field(n.name, _))

    private def compile(n: graph.evaluatedparam.Parameter, ctx: ValidationContext)
                       (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, compiledgraph.evaluatedparam.Parameter] =
      compile(n.expression, ctx).map(compiledgraph.evaluatedparam.Parameter(n.name, _))

    private def compile(n: splittednode.Case, ctx: ValidationContext)
                       (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, (compiledgraph.node.Case, ValidationContext)] =
      A.map2(compile(n.expression, ctx), compile(n.node, ctx))((expr, nextWithCtx) => (compiledgraph.node.Case(expr, nextWithCtx.next), nextWithCtx.ctx))

    private def compile(n: graph.expression.Expression, ctx: ValidationContext)
                       (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, compiledgraph.expression.Expression] = {
      val validParser = expressionParsers
        .get(n.language)
        .map(valid)
        .getOrElse(invalid(NotSupportedExpressionLanguage(n.language)))
      (validParser andThen { parser =>
        val parseResult = if (contextValidationEnabled) {
          parser.parse(n.expression, ctx)
        } else {
          parser.parseWithoutContextValidation(n.expression)
        }
        parseResult.leftMap(err => ExpressionParseError(err.message, n.expression))
      }).toValidatedNel
    }
  }

  protected def createServiceInvoker(obj: ParametersProviderT): ServiceInvoker


}

object PartSubGraphCompilerBase {

  private[compile] def defaultParsers(globalProcessVariables: Map[String, ClazzRef]) = {
    val parsersSeq = Seq(SpelExpressionParser.default(globalProcessVariables))
    parsersSeq.map(p => p.languageId -> p).toMap
  }

  case class CompiledNode(node: compiledgraph.node.Node, ctx: ValidationContext)
  case class NextWithContext(next: compiledgraph.node.Next, ctx: ValidationContext)

}

object PartSubGraphCompiler {

  def default(servicesDefs: Map[String, ObjectWithMethodDef], globalProcessVariables: Map[String, ClazzRef]): PartSubGraphCompiler = {
    new PartSubGraphCompiler(PartSubGraphCompilerBase.defaultParsers(globalProcessVariables), servicesDefs)
  }
}

object PartSubGraphValidator {

  def default(services: Map[String, ObjectDefinition], globalProcessVariables: Map[String, ClazzRef]) = {
    new PartSubGraphValidator(PartSubGraphCompilerBase.defaultParsers(globalProcessVariables), services)
  }

}