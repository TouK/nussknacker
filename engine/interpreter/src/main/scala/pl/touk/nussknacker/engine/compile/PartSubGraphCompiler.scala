package pl.touk.nussknacker.engine.compile

import cats.data.Validated._
import cats.data.{NonEmptyList,  ValidatedNel}
import cats.instances.list._
import cats.instances.option._
import cats.kernel.Semigroup
import pl.touk.nussknacker.engine.api.{Context, MetaData}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.{ClazzRef, ServiceReturningType}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.compile.ProcessCompilationError._
import pl.touk.nussknacker.engine.compiledgraph.node
import pl.touk.nussknacker.engine.compiledgraph.node.{Node, SubprocessEnd}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ExpressionDefinition
import pl.touk.nussknacker.engine.definition._
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.splittedgraph._
import pl.touk.nussknacker.engine.splittedgraph.splittednode.{Next, SplittedNode}
import pl.touk.nussknacker.engine.{compiledgraph, _}
import pl.touk.nussknacker.engine.util.Implicits._

import scala.util.{Failure, Success, Try}


class PartSubGraphCompiler(protected val classLoader: ClassLoader,
                           protected val expressionCompiler: ExpressionCompiler,
                           protected val expressionConfig: ExpressionDefinition[ObjectWithMethodDef],
                           protected val services: Map[String, ObjectWithMethodDef]) {

  type ParametersProviderT = ObjectWithMethodDef

  private val syntax = ValidatedSyntax[ProcessCompilationError]

  import CompilationResult._
  import syntax._

  //FIXME: should it be here?
  private val expressionEvaluator = {
    val globalVars = expressionConfig.globalVariables.mapValuesNow(_.obj)
    ExpressionEvaluator.withoutLazyVals(globalVars, List())
  }

  def validate(n: splittednode.SplittedNode[_], ctx: ValidationContext): CompilationResult[Unit] = {
    compile(n, ctx).map(_ => ())
  }

  protected def createServiceInvoker(obj: ObjectWithMethodDef) = ServiceInvoker(obj)

  private val globalVariableTypes = expressionConfig.globalVariables.mapValues(_.returnType)

  def compile(n: SplittedNode[_], ctx: ValidationContext) : CompilationResult[compiledgraph.node.Node] = {
    implicit val nodeId: NodeId = NodeId(n.id)

    val nodeResult : CompilationResult[compiledgraph.node.Node] = n match {
      case splittednode.SourceNode(nodeData, next) => handleSourceNode(nodeData, ctx, next)
      case splittednode.OneOutputSubsequentNode(data, next) => compileSubsequent(ctx, data, next)

      case splittednode.SplitNode(bareNode, nexts) =>
        val compiledNexts = nexts.map(n => compile(n, ctx)).sequence
        compiledNexts.map(nx => compiledgraph.node.SplitNode(bareNode.id, nx))

      case splittednode.FilterNode(f@graph.node.Filter(id, expression, _, _), nextTrue, nextFalse) =>
        CompilationResult.map3(CompilationResult(compile(expression, None, ctx, Typed[Boolean])._2), compile(nextTrue, ctx), nextFalse.map(next => compile(next, ctx)).sequence)(
          (expr, next, nextFalse) =>
            compiledgraph.node.Filter(id = id,
            expression = expr,
            nextTrue = next,
            nextFalse = nextFalse,
            isDisabled = f.isDisabled.contains(true)))

      case splittednode.SwitchNode(graph.node.Switch(id, expression, exprVal, _), nexts, defaultNext) =>
        val (newCtx, compiledExpression) = withVariable(exprVal, ctx, compile(expression, None, ctx, Unknown))
        CompilationResult.map3(CompilationResult(compiledExpression), nexts.map(n => compile(n, newCtx)).sequence, defaultNext.map(dn => compile(dn, newCtx)).sequence)(
          (realCompiledExpression, cases, next) => {
            compiledgraph.node.Switch(id, realCompiledExpression, exprVal, cases, next)
          })
      case splittednode.EndingNode(data) => CompilationResult(compileEndingNode(ctx, data))

    }
    nodeResult.copy(typing = nodeResult.typing + (n.id -> ctx))
  }

  private def handleSourceNode(nodeData: StartingNodeData, ctx: ValidationContext, next: splittednode.Next): CompilationResult[node.Source] = {
    nodeData match {
      case graph.node.Source(id, _, _) =>
        compile(next, ctx).map(nwc => compiledgraph.node.Source(id, nwc))
      case graph.node.Join(id, _, _, _) =>
        compile(next, ctx).map(nwc => compiledgraph.node.Source(id, nwc))
      case SubprocessInputDefinition(id, _, _) =>
        //TODO: should we recognize we're compiling only subprocess?
        compile(next, ctx).map(nwc => compiledgraph.node.Source(id, nwc))
    }
  }

  private def compileEndingNode(ctx: ValidationContext, data: EndingNodeData)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Node] = data match {
    case graph.node.Processor(id, ref, disabled, _) =>
      compile(ref, ctx).map(cn => compiledgraph.node.EndingProcessor(id, cn._1, disabled.contains(true)))
    case graph.node.Sink(id, ref, optionalExpression, disabled, _) =>
      optionalExpression.map(oe => compile(oe, None, ctx, Unknown)._2).sequence.map(typed => compiledgraph.node.Sink(id, ref.typ, typed, disabled.contains(true)))
    //probably this shouldn't occur - otherwise we'd have empty subprocess?
    case SubprocessInput(id, _, _, _, _) => Invalid(NonEmptyList.of(UnresolvedSubprocess(id)))
    case SubprocessOutputDefinition(id, name, disabled) =>
      //TODO: should we validate it's process?
      Valid(compiledgraph.node.Sink(id, name, None, disabled.contains(true)))

    //TODO JOIN: a lot of additional validations needed here - e.g. that join with that name exists, that it
    //accepts this join, maybe we should also validate the graph is connected?
    case BranchEndData(id, joinId) => Valid(compiledgraph.node.BranchEnd(id, joinId.joinId))
  }

  private def compileSubsequent(ctx: ValidationContext, data: OneOutputSubsequentNodeData, next: Next)(implicit nodeId: NodeId): CompilationResult[Node] = data match {
    case graph.node.Variable(id, varName, expression, _) =>
      val (newCtx, compiledExpression) = withVariable(varName, ctx, compile(expression, None, ctx, Unknown))

      CompilationResult.map2(CompilationResult(compiledExpression), compile(next, newCtx)) { (compiled, compiledNext) =>
        compiledgraph.node.VariableBuilder(id, varName, Left(compiled), compiledNext)
      }
    case graph.node.VariableBuilder(id, varName, fields, _) =>
      val fieldsCompiled = fields.map(f => compile(f, ctx)).unzip
      val fieldsTyped = (TypedObjectTypingResult(fieldsCompiled._1.toMap), fieldsCompiled._2.sequence)

      val (newCtx, compiledVariable) = withVariable(varName, ctx, fieldsTyped)

      CompilationResult.map2(CompilationResult(compiledVariable), compile(next, newCtx)) { (compiledFields, compiledNext) =>
        compiledgraph.node.VariableBuilder(id, varName, Right(compiledFields), compiledNext)
      }

    case graph.node.Processor(id, ref, isDisabled, _) =>
      CompilationResult.map2(CompilationResult(compile(ref, ctx)), compile(next, ctx))((ref, next) =>
        compiledgraph.node.Processor(id, ref._1, next, isDisabled.contains(true)))

    case graph.node.Enricher(id, ref, outName, _) =>
      val compiledRef = compile(ref, ctx)

      val newCtx = compiledRef.andThen { case (_, returnTypeFromRef) =>
        val returnType = returnTypeFromRef.orElse(services.get(ref.id).map(_.returnType)).getOrElse(Unknown)
        ctx.withVariable(outName, returnType)
      }
      CompilationResult.map3(CompilationResult(newCtx), CompilationResult(compile(ref, ctx)), compile(next, newCtx.getOrElse(ctx)))((_, ref, next) =>
                         compiledgraph.node.Enricher(id, ref._1, outName, next))

    //here we don't do anything, in subgraphcompiler it's just pass through
    case graph.node.CustomNode(id, _, _, _, _) =>
      CompilationResult.map(
        fa = compile(next, ctx))(
        f = compiledNext => compiledgraph.node.CustomNode(id, compiledNext))

    case subprocessInput@SubprocessInput(id, ref, _, _, subParams) =>
      import cats.implicits.toTraverseOps

      val childCtx = ctx.pushNewContext(globalVariableTypes)
      val newCtx = ref.parameters.foldLeft[ValidatedNel[ProcessCompilationError, ValidationContext]](Valid(childCtx))
                    { case (accCtx, param) => accCtx.andThen(_.withVariable(param.name, Unknown))}

      val validParamDefs =
        ref.parameters.map(p => getSubprocessParamDefinition(subprocessInput, p.name)).sequence

      val validParams = validParamDefs.andThen { paramDefs =>
        expressionCompiler.compileObjectParameters(paramDefs, ref.parameters, toOption(ctx))
      }

      CompilationResult.map3(
        f0 = CompilationResult(validParams),
        f1 = compile(next, newCtx.getOrElse(childCtx)),
        f2 = CompilationResult(newCtx))((params, next, _) => compiledgraph.node.SubprocessStart(id, params, next))

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
                     (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, (compiledgraph.service.ServiceRef, Option[TypingResult])] = {
    val service = services.get(n.id).map(Valid(_)).getOrElse(invalid(MissingService(n.id))).toValidatedNel

    service.andThen { objWithMethod =>
      expressionCompiler.compileObjectParameters(objWithMethod.parameters, n.parameters, toOption(ctx)).map { params =>
          val invoker = createServiceInvoker(objWithMethod)
          (compiledgraph.service.ServiceRef(n.id, invoker, params), computeReturnType(objWithMethod.obj, params))
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
    CompilationResult.map2(CompilationResult(compile(n.expression, None, ctx, Typed[Boolean])._2), compile(n.node, ctx))((expr, next) => compiledgraph.node.Case(expr, next))

  private def compile(n: graph.variable.Field, ctx: ValidationContext)
                     (implicit nodeId: NodeId): ((String, TypingResult), ValidatedNel[ProcessCompilationError, compiledgraph.variable.Field]) = {
    val compiled = compile(n.expression, Some(n.name), ctx, Unknown)
    ((n.name, compiled._1), compiled._2.map(compiledgraph.variable.Field(n.name, _)))
  }

  private def compile(n: graph.expression.Expression,
                      fieldName: Option[String],
                      ctx: ValidationContext,
                      expectedType: TypingResult)
                     (implicit nodeId: NodeId): (TypingResult, ValidatedNel[ProcessCompilationError, compiledgraph.expression.Expression]) = {
    expressionCompiler.compile(n, fieldName, toOption(ctx), expectedType)
      .fold(err => (Unknown, Invalid(err)), res => (res._1, Valid(res._2)))
  }

  private def toOption(ctx: ValidationContext) = Some(ctx)

  //this method tries to compute constant parameters if service is ServiceReturningType
  //TODO: is it right way to do this? Maybe we just need to analyze Expression?
  private def computeReturnType(service: Any,
                                parameters: List[compiledgraph.evaluatedparam.Parameter]): Option[TypingResult] = service match {
    case srt: ServiceReturningType =>

      val data = parameters.map { param =>
        param.name -> (param.returnType, tryToEvaluateParam(param))
      }.toMap
      Some(srt.returnType(data))
    case _ => None
  }

  /*
      we try to evaluate parameter, but if it fails (e.g. it contains variable), or future does not complete immediately - we just return None
   */
  private def tryToEvaluateParam(param: compiledgraph.evaluatedparam.Parameter): Option[Any] = {
    import pl.touk.nussknacker.engine.util.SynchronousExecutionContext._
    implicit val meta: MetaData = MetaData("", null)
    Try {
      val futureValue = expressionEvaluator.evaluate[Any](param.expression, "", "", Context(""))
      futureValue.value.flatMap(_.toOption).map(_.value)
    }.toOption.flatten
  }

  private def getSubprocessParamDefinition(subprocessInput: SubprocessInput, paramName: String): ValidatedNel[PartSubGraphCompilationError, Parameter] = {
    val subParam = subprocessInput.subprocessParams.get.find(_.name == paramName).get
    subParam.typ.toTyped(classLoader) match {
      case Success(typingResult) =>
        valid(Parameter(paramName, typingResult, typingResult))
      case Failure(_) =>
        invalid(
          SubprocessParamClassLoadError(paramName, subParam.typ, subprocessInput.id)
        ).toValidatedNel
    }
  }
}
