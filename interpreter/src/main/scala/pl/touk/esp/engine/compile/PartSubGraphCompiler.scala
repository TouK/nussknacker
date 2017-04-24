package pl.touk.esp.engine.compile

import cats.data.Validated._
import cats.data.{NonEmptyList, ValidatedNel}
import cats.instances.list._
import cats.instances.option._
import pl.touk.esp.engine._
import pl.touk.esp.engine.compile.PartSubGraphCompilerBase.{CompiledNode, ContextsForParts, NextWithContext}
import pl.touk.esp.engine.compile.ProcessCompilationError._
import pl.touk.esp.engine.compile.dumb._
import pl.touk.esp.engine.compiledgraph.evaluatedparam.Parameter
import pl.touk.esp.engine.compiledgraph.expression.ExpressionParser
import pl.touk.esp.engine.compiledgraph.node.{NextNode, PartRef, SubprocessEnd}
import pl.touk.esp.engine.definition.DefinitionExtractor.{ClazzRef, _}
import pl.touk.esp.engine.definition._
import pl.touk.esp.engine.graph.node._
import pl.touk.esp.engine.spel.SpelExpressionParser
import pl.touk.esp.engine.splittedgraph._
import pl.touk.esp.engine.splittedgraph.splittednode.SplittedNode

import scala.util.Right

class PartSubGraphCompiler(protected val expressionParsers: Map[String, ExpressionParser],
                           protected val services: Map[String, ObjectWithMethodDef]) extends PartSubGraphCompilerBase {

  override type ParametersProviderT = ObjectWithMethodDef

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

  def validate(n: splittednode.SplittedNode[_], ctx: ValidationContext): ValidatedNel[PartSubGraphCompilationError, ContextsForParts] = {
    compile(n, ctx).map(_.ctx)
  }

  protected def compile(n: splittednode.SplittedNode[_], ctx: ValidationContext): ValidatedNel[PartSubGraphCompilationError, CompiledNode] = {
    new Compiler(true).doCompile(n, ctx)
  }

  import syntax._

  def validateWithoutContextValidation(n: splittednode.SplittedNode[_]): ValidatedNel[PartSubGraphCompilationError, ContextsForParts] = {
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
  //ten kod kompiluje pojedynczego parta (to co bedzie w jednym wezle flinka)
  //na wyjsciu dostajemy takze informacje o tym jakie moga byc dalsze kroki (PartRef) i jakie wtedy sa tam zmienne
  private class Compiler(contextValidationEnabled: Boolean) {

    type ValidatedWithCompiler[A] = ValidatedNel[PartSubGraphCompilationError, A]

    def doCompile(n: SplittedNode[_], ctx: ValidationContext): ValidatedNel[PartSubGraphCompilationError, CompiledNode] = {
      implicit val nodeId = NodeId(n.id)
      n match {
        case splittednode.SourceNode(graph.node.Source(id, ref, _), next) =>
          compile(next, ctx).map(nwc => CompiledNode(compiledgraph.node.Source(id, nwc.next), nwc.ctx))
        case splittednode.OneOutputSubsequentNode(data: OneOutputSubsequentNodeData, next) =>
          data match {
            case graph.node.Variable(id, varName, expression, _) =>
              ctx.withVariable(varName, ClazzRef(classOf[Any])).andThen { newCtx => //jak wyciagnac informacie o typie zmiennej?
                A.map2(compile(expression, None, ctx), compile(next, newCtx))(
                  (compiledExpression, nextWithCtx) =>
                    CompiledNode(compiledgraph.node.VariableBuilder(id, varName, Left(compiledExpression), nextWithCtx.next), nextWithCtx.ctx))
              }
            case graph.node.VariableBuilder(id, varName, fields, _) =>
              ctx.withVariable(varName, ClazzRef(classOf[Map[String, Any]])).andThen { newCtx =>  //jak wyciagnac informacie o typach zmiennych w mapie?
                A.map2(fields.map(f => compile(f, ctx)).sequence, compile(next, newCtx))(
                  (fields, nextWithCtx) =>
                    CompiledNode(compiledgraph.node.VariableBuilder(id, varName, Right(fields), nextWithCtx.next), nextWithCtx.ctx))
              }

            case graph.node.Processor(id, ref, isDisabled, _) =>
              A.map2(compile(ref, ctx), compile(next, ctx))((ref, nextWithCtx) =>
                CompiledNode(compiledgraph.node.Processor(id, ref, nextWithCtx.next, isDisabled.contains(true)), nextWithCtx.ctx))
            case graph.node.Enricher(id, ref, outName, _) =>
              services.get(ref.id).map { definition =>
                ctx.withVariable(outName, definition.returnType)
              }.getOrElse(Valid(ctx)).andThen { newCtx =>
                A.map2(compile(ref, newCtx), compile(next, newCtx))((ref, nextWithCtx) =>
                  CompiledNode(compiledgraph.node.Enricher(id, ref, outName, nextWithCtx.next), nextWithCtx.ctx))
              }

            //tu nie dodajemy do kontekstu zmiennej, bo to jest obslugiwane gdzie indziej (teraz we flinku :|)
            case graph.node.CustomNode(id, _, customNodeRef, parameters, _) =>
              //TODO: na razie zakladamy ze nie resetujemy kontekstu, output mamy z gory
              //TODO: nie walidujemy parametrow ze zmiennymi, bo nie wiemy co doda CustomNode
              val validParams = parameters.map(p => compileParam(p, ctx, skipContextValidation = true)).sequence
              A.map2(validParams, compile(next, ctx))((params, nextWithCtx) =>
                CompiledNode(compiledgraph.node.CustomNode(id, params, nextWithCtx.next), nextWithCtx.ctx))
            case SubprocessInput(id, ref, _) =>
              //TODO: typowanie zmiennych?
              ref.parameters.foldLeft(Valid(ctx.pushNewContext()).asInstanceOf[ValidatedNel[PartSubGraphCompilationError, ValidationContext]])
                { case (accCtx, param) => accCtx.andThen(_.withVariable(param.name, ClazzRef[Any]))}.andThen { ctxWithVars =>
                  val validParams = ref.parameters.map(p => compileParam(p, ctx)).sequence
                  A.map2(validParams, compile(next, ctxWithVars))((params, nextWithCtx) =>
                    CompiledNode(compiledgraph.node.SubprocessStart(id, params, nextWithCtx.next), nextWithCtx.ctx))
                  }
            case SubprocessOutput(id, _, _) =>
              ctx.popContext.andThen { popContext =>
                compile(next, popContext).map { nextWithCtx =>
                  CompiledNode(SubprocessEnd(id, nextWithCtx.next), nextWithCtx.ctx)
                }
              }
          }
        case splittednode.SplitNode(bareNode, nexts) =>
          nexts.map(n => compile(n.next, ctx)).sequence.map { parts =>
            CompiledNode(compiledgraph.node.SplitNode(bareNode.id), parts.flatMap(_.ctx).toMap)
          }
        case splittednode.FilterNode(f@graph.node.Filter(id, expression, _, _), nextTrue, nextFalse) =>
          A.map3(compile(expression, None, ctx), compile(nextTrue, ctx), nextFalse.map(next => compile(next, ctx)).sequence)(
            (expr, nextWithCtx, nextWithCtxFalse) => CompiledNode(compiledgraph.node.Filter(id, expr, nextWithCtx.next,
              nextWithCtxFalse.map(_.next), f.isDisabled.contains(true)),
              nextWithCtx.ctx ++ nextWithCtxFalse.map(_.ctx).getOrElse(Map())))
        case splittednode.SwitchNode(graph.node.Switch(id, expression, exprVal, _), nexts, defaultNext) =>
          ctx.withVariable(exprVal, ClazzRef(classOf[Any])).andThen { newCtx =>
            A.map3(compile(expression, None, newCtx), nexts.map(n => compile(n, newCtx)).sequence, defaultNext.map(dn => compile(dn, newCtx)).sequence)(
              (expr, cases, nextWithCtx) => {
                val defaultCtx = nextWithCtx.map(_.ctx).getOrElse(Map())
                CompiledNode(compiledgraph.node.Switch(id, expr, exprVal, cases.unzip._1, nextWithCtx.map(_.next)),
                  cases.unzip._2.fold(defaultCtx)(_ ++ _))
              })
          }

        case splittednode.EndingNode(data: EndingNodeData) =>
          //tu dajemy puste mapy, bo dalej nic juz nie powinno byc
          data match {
            case graph.node.Processor(id, ref, disabled, _) =>
              compile(ref, ctx).map(compiledgraph.node.EndingProcessor(id, _, disabled.contains(true))).map(CompiledNode(_, Map()))
            case graph.node.Sink(id, ref, optionalExpression, _) =>
              optionalExpression.map(oe => compile(oe, None, ctx)).sequence.map(compiledgraph.node.Sink(id, ref.typ, _)).map(CompiledNode(_, Map()))
            //no chyba tutaj tego nie powinno byc, bo to bylby pusty podproces??
            case SubprocessInput(id, _, _) => Invalid(NonEmptyList.of(UnresolvedSubprocess(id)))
            case SubprocessOutputDefinition(id, _, _) => Invalid(NonEmptyList.of(UnresolvedSubprocess(id)))
          }
      }
    }

    private def compile(next: splittednode.Next, ctx: ValidationContext): ValidatedNel[PartSubGraphCompilationError, NextWithContext] = {
      next match {
        case splittednode.NextNode(n) => doCompile(n, ctx).map(cn => NextWithContext(compiledgraph.node.NextNode(cn.node), cn.ctx))
        case splittednode.PartRef(ref) => valid(NextWithContext(compiledgraph.node.PartRef(ref), Map(ref -> ctx)))
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
      Validations.validateParameters(parameterProvider.parameters.map(_.name), usedParamNames)
    }

    private def compile(n: graph.variable.Field, ctx: ValidationContext)
                       (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, compiledgraph.variable.Field] =
      compile(n.expression, Some(n.name), ctx).map(compiledgraph.variable.Field(n.name, _))

    private def compileParam(n: graph.evaluatedparam.Parameter, ctx: ValidationContext, skipContextValidation: Boolean = false)
                            (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, compiledgraph.evaluatedparam.Parameter] =
      compile(n.expression, Some(n.name), ctx, skipContextValidation).map(compiledgraph.evaluatedparam.Parameter(n.name, _))

    private def compile(n: splittednode.Case, ctx: ValidationContext)
                       (implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, (compiledgraph.node.Case, ContextsForParts)] =
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

  private[compile] def defaultParsers(globalProcessVariables: Map[String, ClazzRef], loader: ClassLoader) = {
    val parsersSeq = Seq(SpelExpressionParser.default(globalProcessVariables, loader))
    parsersSeq.map(p => p.languageId -> p).toMap
  }

  type PartId = String

  type ContextsForParts = Map[PartId, ValidationContext]

  case class CompiledNode(node: compiledgraph.node.Node, ctx: ContextsForParts)

  case class NextWithContext(next: compiledgraph.node.Next, ctx: ContextsForParts)

}

object PartSubGraphCompiler {

  def default(servicesDefs: Map[String, ObjectWithMethodDef], globalProcessVariables: Map[String, ClazzRef], loader: ClassLoader): PartSubGraphCompiler = {
    new PartSubGraphCompiler(PartSubGraphCompilerBase.defaultParsers(globalProcessVariables, loader), servicesDefs)
  }
}

object PartSubGraphValidator {

  def default(services: Map[String, ObjectDefinition], globalProcessVariables: Map[String, ClazzRef], loader: ClassLoader) = {
    new PartSubGraphValidator(PartSubGraphCompilerBase.defaultParsers(globalProcessVariables, loader), services)
  }

}