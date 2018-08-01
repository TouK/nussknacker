package pl.touk.nussknacker.engine.compile

import cats.data.Validated._
import cats.data.{NonEmptyList, ValidatedNel}
import cats.instances.list._
import cats.instances.option._
import cats.kernel.Semigroup
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.{TypedMapTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.compile.ProcessCompilationError._
import pl.touk.nussknacker.engine.compile.dumb._
import pl.touk.nussknacker.engine.compiledgraph.node.{Node, SubprocessEnd}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ExpressionDefinition
import pl.touk.nussknacker.engine.definition._
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.splittedgraph._
import pl.touk.nussknacker.engine.splittedgraph.splittednode.{Next, SplittedNode}
import pl.touk.nussknacker.engine.{compiledgraph, _}

class PartSubGraphCompiler(protected val expressionCompiler: ExpressionCompiler,
                           protected val expressionConfig: ExpressionDefinition[ObjectWithMethodDef],
                           protected val services: Map[String, ObjectWithMethodDef],
                           protected val customStreamTransformers: Map[String, ObjectWithMethodDef]) extends PartSubGraphCompilerBase {

  override type ParametersProviderT = ObjectWithMethodDef

  override protected def createServiceInvoker(obj: ObjectWithMethodDef) =
    ServiceInvoker(obj)

}

class PartSubGraphValidator(protected val expressionCompiler: ExpressionCompiler,
                            protected val expressionConfig: ExpressionDefinition[ObjectWithMethodDef],
                            protected val services: Map[String, ObjectDefinition],
                            protected val customStreamTransformers: Map[String, ObjectDefinition]) extends PartSubGraphCompilerBase {

  override type ParametersProviderT = ObjectDefinition

  override protected def createServiceInvoker(obj: ObjectDefinition) =
    DumbServiceInvoker

}

private[compile] trait PartSubGraphCompilerBase {

  type ParametersProviderT <: ObjectMetadata

  private val syntax = ValidatedSyntax[ProcessCompilationError]

  /**
    * 'nextCtx' is context after CustomNode and it's used to compile the node following CustomNode,
    * while `ctx` is used to validate params in CustomNode
    */
  // TODO: CustomNode validation refactor
  def validate(n: splittednode.SplittedNode[_], ctx: ValidationContext, nextCtx: Option[ValidationContext] = None): CompilationResult[Unit] = {
    compile(n, ctx, nextCtx).map(_ => ())
  }

  import CompilationResult._
  import syntax._

  protected def expressionCompiler: ExpressionCompiler

  protected def expressionConfig: ExpressionDefinition[ObjectWithMethodDef]

  protected def services: Map[String, ParametersProviderT]

  protected def customStreamTransformers: Map[String, ParametersProviderT]

  protected def createServiceInvoker(obj: ParametersProviderT): ServiceInvoker

  private val globalVariableTypes = expressionConfig.globalVariables.mapValues(_.returnType)

  def compile(n: SplittedNode[_], ctx: ValidationContext, nextCtx: Option[ValidationContext] = None) : CompilationResult[compiledgraph.node.Node] = {
    implicit val nodeId: NodeId = NodeId(n.id)

    val nodeResult : CompilationResult[compiledgraph.node.Node] = n match {
      case splittednode.SourceNode(graph.node.Source(id, _, _), next) =>
        compile(next, ctx).map(nwc => compiledgraph.node.Source(id, nwc))
      case splittednode.SourceNode(SubprocessInputDefinition(id, _, _), next) =>
        //TODO: should we recognize we're compiling only subprocess?
        compile(next, ctx).map(nwc => compiledgraph.node.Source(id, nwc))
      case splittednode.OneOutputSubsequentNode(data, next) => compileSubsequent(ctx, data, next, nextCtx.getOrElse(ctx))

      case splittednode.SplitNode(bareNode, nexts) =>
        val compiledNexts = nexts.map(n => compile(n.next, ctx)).sequence
        compiledNexts.map(_ => compiledgraph.node.SplitNode(bareNode.id))

      case splittednode.FilterNode(f@graph.node.Filter(id, expression, _, _), nextTrue, nextFalse) =>
        CompilationResult.map3(CompilationResult(compile(expression, None, ctx, ClazzRef[Boolean])._2), compile(nextTrue, ctx), nextFalse.map(next => compile(next, ctx)).sequence)(
          (expr, next, nextFalse) =>
            compiledgraph.node.Filter(id = id,
            expression = expr,
            nextTrue = next,
            nextFalse = nextFalse,
            isDisabled = f.isDisabled.contains(true)))

      case splittednode.SwitchNode(graph.node.Switch(id, expression, exprVal, _), nexts, defaultNext) =>
        val (newCtx, compiledExpression) = withVariable(exprVal, ctx, compile(expression, None, ctx, ClazzRef[Any]))
        CompilationResult.map3(CompilationResult(compiledExpression), nexts.map(n => compile(n, newCtx)).sequence, defaultNext.map(dn => compile(dn, newCtx)).sequence)(
          (realCompiledExpression, cases, next) => {
            compiledgraph.node.Switch(id, realCompiledExpression, exprVal, cases, next)
          })
      case splittednode.EndingNode(data) => CompilationResult(compileEndingNode(ctx, data))
    }
    nodeResult.copy(typing = nodeResult.typing + (n.id -> ctx))
  }

  private def compileEndingNode(ctx: ValidationContext, data: EndingNodeData)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Node] = data match {
    case graph.node.Processor(id, ref, disabled, _) =>
      compile(ref, ctx).map(compiledgraph.node.EndingProcessor(id, _, disabled.contains(true)))
    case graph.node.Sink(id, ref, optionalExpression, disabled, _) =>
      optionalExpression.map(oe => compile(oe, None, ctx, ClazzRef[Any])._2).sequence.map(typed => compiledgraph.node.Sink(id, ref.typ, typed, disabled.contains(true)))
    //probably this shouldn't occur - otherwise we'd have empty subprocess?
    case SubprocessInput(id, _, _, _) => Invalid(NonEmptyList.of(UnresolvedSubprocess(id)))   
    case SubprocessOutputDefinition(id, name, disabled) =>
      //TODO: should we validate it's process?
      Valid(compiledgraph.node.Sink(id, name, None, disabled.contains(true)))
  }

  private def compileSubsequent(ctx: ValidationContext, data: OneOutputSubsequentNodeData, next: Next, nextCtx: ValidationContext)(implicit nodeId: NodeId): CompilationResult[Node] = data match {
    case graph.node.Variable(id, varName, expression, _) =>
      val (newCtx, compiledExpression) = withVariable(varName, ctx, compile(expression, None, ctx, ClazzRef[Any]))

      CompilationResult.map2(CompilationResult(compiledExpression), compile(next, newCtx)) { (compiled, compiledNext) =>
        compiledgraph.node.VariableBuilder(id, varName, Left(compiled), compiledNext)
      }
    case graph.node.VariableBuilder(id, varName, fields, _) =>
      val fieldsCompiled = fields.map(f => compile(f, ctx)).unzip
      val fieldsTyped = (TypedMapTypingResult(fieldsCompiled._1.toMap), fieldsCompiled._2.sequence)

      val (newCtx, compiledVariable) = withVariable(varName, ctx, fieldsTyped)

      CompilationResult.map2(CompilationResult(compiledVariable), compile(next, newCtx)) { (compiledFields, compiledNext) =>
        compiledgraph.node.VariableBuilder(id, varName, Right(compiledFields), compiledNext)
      }

    case graph.node.Processor(id, ref, isDisabled, _) =>
      CompilationResult.map2(CompilationResult(compile(ref, ctx)), compile(next, ctx))((ref, next) =>
        compiledgraph.node.Processor(id, ref, next, isDisabled.contains(true)))

    case graph.node.Enricher(id, ref, outName, _) =>
      val newCtx = ctx.withVariable(outName, services.get(ref.id).map(_.returnType).getOrElse(Unknown))
      CompilationResult.map3(CompilationResult(newCtx), CompilationResult(compile(ref, ctx)), compile(next, newCtx.getOrElse(ctx)))((_, ref, next) =>
                         compiledgraph.node.Enricher(id, ref, outName, next))

    // we don't put variable in context here, as it's handled in flink currently (maybe try to change it?)
    // TODO: handle cases when context variables are not being used
    case graph.node.CustomNode(id, _, typ, evaluatedParams, _) => {
      val validatedParamsProvider = customStreamTransformers.get(typ).map(Valid(_))
          .getOrElse(invalid(MissingCustomNodeExecutor(typ))).toValidatedNel

      val validatedParams = validatedParamsProvider andThen { provider =>
        val params = provider.parameters
        val paramsCtx = params.map(param => {
          param.name -> ValidationContext(ctx.variables ++ param.additionalVariables)
        }).toMap
       expressionCompiler.compileObjectParameters(params, evaluatedParams, paramsCtx)
      }

      CompilationResult.map2(
        fa = CompilationResult(validatedParams),
        fb = compile(next, nextCtx))(
        f = (compiledParams, compiledNext) => compiledgraph.node.CustomNode(id, compiledParams, compiledNext))
    }

    case SubprocessInput(id, ref, _, _) =>
      val childCtx = ctx.pushNewContext(globalVariableTypes)

      val newCtx = ref.parameters.foldLeft[ValidatedNel[ProcessCompilationError, ValidationContext]](Valid(childCtx))
                    { case (accCtx, param) => accCtx.andThen(_.withVariable(param.name, Unknown))}
      val validParams =
        expressionCompiler.compileObjectParameters(ref.parameters.map(p => Parameter.unknownType(p.name)), ref.parameters, toOption(ctx))

      CompilationResult.map3(CompilationResult(validParams), compile(next, newCtx.getOrElse(childCtx)), CompilationResult(newCtx))((params, next, _) =>
        compiledgraph.node.SubprocessStart(id, params, next))

    case SubprocessOutput(id, _, _) =>
      //this popContext *really* has to work to be able to extract variable types :|
      ctx.popContext.fold(error => CompilationResult(Invalid(error)), popContext =>
        compile(next, popContext).map(SubprocessEnd(id, _))
      )
  }

  private def compile(next: splittednode.Next, ctx: ValidationContext): CompilationResult[compiledgraph.node.Next] = {
    next match {
      case splittednode.NextNode(n) => compile(n, ctx).map(cn => compiledgraph.node.NextNode(cn))
      case splittednode.PartRef(ref) => CompilationResult(Map(ref -> ctx), Valid(compiledgraph.node.PartRef(ref)))
    }
  }

  private def compile(n: graph.service.ServiceRef, ctx: ValidationContext)
                     (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, compiledgraph.service.ServiceRef] = {
    val service = services.get(n.id).map(Valid(_)).getOrElse(invalid(MissingService(n.id))).toValidatedNel

    service.andThen { obj =>
      expressionCompiler.compileObjectParameters(obj.parameters, n.parameters, toOption(ctx)).map { params =>
          val invoker = createServiceInvoker(obj)
          compiledgraph.service.ServiceRef(n.id, invoker, params)
      }
    }
  }

  private def withVariable[R](name: String, validationContext: ValidationContext, typingResult: (TypingResult, ValidatedNel[ProcessCompilationError, R]))(implicit nodeId: NodeId)
    : (ValidationContext, ValidatedNel[ProcessCompilationError, R]) = {
    implicit val firstSemi = new Semigroup[R] { override def combine(x: R, y: R): R = x }
    validationContext.withVariable(name, typingResult._1) match {
      case Valid(newCtx) => (newCtx, typingResult._2)
      case in@Invalid(_) => (validationContext, in.combine(typingResult._2))
    }
  }

  private def compile(n: splittednode.Case, ctx: ValidationContext)
                     (implicit nodeId: NodeId): CompilationResult[compiledgraph.node.Case] =
    CompilationResult.map2(CompilationResult(compile(n.expression, None, ctx, ClazzRef[Boolean])._2), compile(n.node, ctx))((expr, next) => compiledgraph.node.Case(expr, next))

  private def compile(n: graph.variable.Field, ctx: ValidationContext)
                     (implicit nodeId: NodeId): ((String, TypingResult), ValidatedNel[ProcessCompilationError, compiledgraph.variable.Field]) = {
    val compiled = compile(n.expression, Some(n.name), ctx, ClazzRef[Any])
    ((n.name, compiled._1), compiled._2.map(compiledgraph.variable.Field(n.name, _)))
  }

  private def compile(n: graph.expression.Expression,
                      fieldName: Option[String],
                      ctx: ValidationContext,
                      expectedType: ClazzRef)
                     (implicit nodeId: NodeId): (TypingResult, ValidatedNel[ProcessCompilationError, compiledgraph.expression.Expression]) = {
    expressionCompiler.compile(n, fieldName, toOption(ctx), expectedType)
      .fold(err => (Unknown, Invalid(err)), res => (res._1, Valid(res._2)))
  }

  private def toOption(ctx: ValidationContext) = Some(ctx)

}
