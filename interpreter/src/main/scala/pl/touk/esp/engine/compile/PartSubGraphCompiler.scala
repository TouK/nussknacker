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
import pl.touk.esp.engine.compiledgraph.node.{NextNode, PartRef}
import pl.touk.esp.engine.definition.DefinitionExtractor._
import pl.touk.esp.engine.definition._
import pl.touk.esp.engine.graph.node.{EndingNodeData, OneOutputSubsequentNodeData}
import pl.touk.esp.engine.spel.SpelExpressionParser
import pl.touk.esp.engine.splittedgraph._
import pl.touk.esp.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.esp.engine.types.EspTypeUtils

import scala.util.Right

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
  private val syntax = ValidatedSyntax[PartSubGraphCompilationError]

  def validate(n: splittednode.SplittedNode[_], ctx: ValidationContext): ValidatedNel[PartSubGraphCompilationError, ValidationContext] = {
    compile(n, ctx).map(_.ctx)
  }

  protected def compile(n: splittednode.SplittedNode[_], ctx: ValidationContext): ValidatedNel[PartSubGraphCompilationError, CompiledNode] = {
    new Compiler(true).doCompile(n, ctx)
  }

  import syntax._

  def validateWithoutContextValidation(n: splittednode.SplittedNode[_]): ValidatedNel[PartSubGraphCompilationError, ValidationContext] = {
    compileWithoutContextValidation(n).map(_.ctx)
  }

  protected def compileWithoutContextValidation(n: splittednode.SplittedNode[_]): ValidatedNel[PartSubGraphCompilationError, CompiledNode] = {
    new Compiler(false).doCompile(n, ValidationContext())
  }

  protected def expressionParsers: Map[String, ExpressionParser]

  protected def services: Map[String, ParametersProviderT]

  protected def compile(n: splittednode.SplittedNode[_]): ValidatedNel[PartSubGraphCompilationError, CompiledNode] = {
    new Compiler(true).doCompile(n, ValidationContext())
  }

  protected def createServiceInvoker(obj: ParametersProviderT): ServiceInvoker

  //TODO: no to wyglada troche strasznie, nie??
  private class Compiler(contextValidationEnabled: Boolean) {
    def doCompile(n: SplittedNode[_], ctx: ValidationContext): ValidatedNel[PartSubGraphCompilationError, CompiledNode] = {
      implicit val nodeId = NodeId(n.id)
      n match {
        case splittednode.SourceNode(graph.node.Source(id, ref, _), next) =>
          compile(next, ctx).map(nwc => CompiledNode(compiledgraph.node.Source(id, nwc.next), nwc.ctx))
        case splittednode.OneOutputSubsequentNode(data: OneOutputSubsequentNodeData, next) =>
          data match {
            case graph.node.Variable(id, varName, expression, _) =>
              val newCtx = ctx.withVariable(varName, ClazzRef(classOf[Any])) //jak wyciagnac informacie o typie zmiennej?
              A.map2(compile(expression, None, ctx), compile(next, newCtx))(
                (compiledExpression, nextWithCtx) =>
                  CompiledNode(compiledgraph.node.VariableBuilder(id, varName, Left(compiledExpression), nextWithCtx.next), nextWithCtx.ctx))
            case graph.node.VariableBuilder(id, varName, fields, _) =>
              val newCtx = ctx.withVariable(varName, ClazzRef(classOf[Map[String, Any]])) //jak wyciagnac informacie o typach zmiennych w mapie?
              A.map2(fields.map(f => compile(f, ctx)).sequence, compile(next, newCtx))(
                (fields, nextWithCtx) =>
                  CompiledNode(compiledgraph.node.VariableBuilder(id, varName, Right(fields), nextWithCtx.next), nextWithCtx.ctx))
            case graph.node.Processor(id, ref, _) =>
              A.map2(compile(ref, ctx), compile(next, ctx))((ref, nextWithCtx) =>
                CompiledNode(compiledgraph.node.Processor(id, ref, nextWithCtx.next), nextWithCtx.ctx))
            case graph.node.Enricher(id, ref, outName, _) =>
              val newCtx = services.get(ref.id).map { definition =>
                ctx.withVariable(outName, definition.returnType)
              }.getOrElse(ctx)
              A.map2(compile(ref, newCtx), compile(next, newCtx))((ref, nextWithCtx) =>
                CompiledNode(compiledgraph.node.Enricher(id, ref, outName, nextWithCtx.next), nextWithCtx.ctx))
            case graph.node.CustomNode(id, outputVar, customNodeRef, parameters, _) =>
              //TODO: na razie zakladamy ze nie resetujemy kontekstu, output mamy z gory
              //TODO: nie walidujemy parametrow ze zmiennymi, bo nie wiemy co doda CustomNode
              val validParams = parameters.map(p => compileParam(p, ctx, skipContextValidation = true)).sequence
              A.map2(validParams, compile(next, ctx))((params, nextWithCtx) =>
                CompiledNode(compiledgraph.node.CustomNode(id, params, nextWithCtx.next), nextWithCtx.ctx))
          }
        case splittednode.SplitNode(bareNode, nexts) =>
          nexts.map(n => compile(n.next, ctx)).sequence.map { _ =>
            CompiledNode(compiledgraph.node.SplitNode(bareNode.id), ctx)
          }
        case splittednode.FilterNode(graph.node.Filter(id, expression, _), nextTrue, nextFalse) =>
          A.map3(compile(expression, None, ctx), compile(nextTrue, ctx), nextFalse.map(next => compile(next, ctx)).sequence)(
            (expr, nextWithCtx, nextWithCtxFalse) => CompiledNode(compiledgraph.node.Filter(id, expr, nextWithCtx.next,
              nextWithCtxFalse.map(_.next)), nextWithCtxFalse.map(nwc => ValidationContext.merge(nwc.ctx, nextWithCtx.ctx)).getOrElse(nextWithCtx.ctx)))
        case splittednode.SwitchNode(graph.node.Switch(id, expression, exprVal, _), nexts, defaultNext) =>
          val newCtx = ctx.withVariable(exprVal, ClazzRef(classOf[Any]))
          A.map3(compile(expression, None, newCtx), nexts.map(n => compile(n, newCtx)).sequence, defaultNext.map(dn => compile(dn, newCtx)).sequence)(
            (expr, cases, nextWithCtx) => {
              val defaultCtx = nextWithCtx.map(_.ctx).getOrElse(ctx)
              CompiledNode(compiledgraph.node.Switch(id, expr, exprVal, cases.unzip._1, nextWithCtx.map(_.next)),
                cases.unzip._2.fold(defaultCtx)(ValidationContext.merge))
            })
        case splittednode.EndingNode(data: EndingNodeData) =>
          data match {
            case graph.node.Processor(id, ref, _) =>
              compile(ref, ctx).map(compiledgraph.node.EndingProcessor(id, _)).map(CompiledNode(_, ctx))
            case graph.node.Sink(id, ref, optionalExpression, _) =>
              optionalExpression.map(oe => compile(oe, None, ctx)).sequence.map(compiledgraph.node.Sink(id, ref.typ, _)).map(CompiledNode(_, ctx))
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
      val validParams = n.parameters.map(p => compileParam(p, ctx)).sequence
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
      val notUsedParams = definedParamNames.diff(usedParamNamesSet)
      if (redundantParams.nonEmpty) {
        invalid(RedundantParameters(redundantParams)).toValidatedNel
      } else if (notUsedParams.nonEmpty) {
        invalid(MissingParameters(notUsedParams)).toValidatedNel
      } else {
        valid(Unit)
      }
    }

    private def compile(n: graph.variable.Field, ctx: ValidationContext)
                       (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, compiledgraph.variable.Field] =
      compile(n.expression, Some(n.name), ctx).map(compiledgraph.variable.Field(n.name, _))

    private def compileParam(n: graph.evaluatedparam.Parameter, ctx: ValidationContext, skipContextValidation: Boolean = false)
                            (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, compiledgraph.evaluatedparam.Parameter] =
      compile(n.expression, Some(n.name), ctx, skipContextValidation).map(compiledgraph.evaluatedparam.Parameter(n.name, _))

    private def compile(n: splittednode.Case, ctx: ValidationContext)
                       (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, (compiledgraph.node.Case, ValidationContext)] =
      A.map2(compile(n.expression, None, ctx), compile(n.node, ctx))((expr, nextWithCtx) => (compiledgraph.node.Case(expr, nextWithCtx.next), nextWithCtx.ctx))

    private def compile(n: graph.expression.Expression, fieldName: Option[String], ctx: ValidationContext, skipContextValidation: Boolean = false)
                       (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, compiledgraph.expression.Expression] = {
      val validParser = expressionParsers
        .get(n.language)
        .map(valid)
        .getOrElse(invalid(NotSupportedExpressionLanguage(n.language)))
      (validParser andThen { parser =>
        val parseResult = if (contextValidationEnabled && !skipContextValidation) {
          parser.parse(n.expression, ctx)
        } else {
          parser.parseWithoutContextValidation(n.expression)
        }
        parseResult.leftMap(err => ExpressionParseError(err.message, fieldName, n.expression))
      }).toValidatedNel
    }
  }


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