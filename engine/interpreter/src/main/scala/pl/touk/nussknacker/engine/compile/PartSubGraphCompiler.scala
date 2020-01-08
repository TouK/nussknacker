package pl.touk.nussknacker.engine.compile

import cats.data.Validated._
import cats.data.{NonEmptyList, ValidatedNel}
import cats.instances.list._
import cats.instances.option._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context.{PartSubGraphCompilationError, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.expression.{ExpressionParser, ExpressionTypingInfo}
import pl.touk.nussknacker.engine.api.typed.ServiceReturningType
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{Context, MetaData}
import pl.touk.nussknacker.engine.compiledgraph.node
import pl.touk.nussknacker.engine.compiledgraph.node.{Node, SubprocessEnd}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ExpressionDefinition
import pl.touk.nussknacker.engine.definition._
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.splittedgraph._
import pl.touk.nussknacker.engine.splittedgraph.splittednode.{Next, SplittedNode}
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax
import pl.touk.nussknacker.engine.{api, compiledgraph, _}

import scala.util.{Failure, Success, Try}
import PartSubGraphCompiler._
import NodeTypingInfo.DefaultExpressionId
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

class PartSubGraphCompiler(protected val classLoader: ClassLoader,
                           protected val expressionCompiler: ExpressionCompiler,
                           protected val expressionConfig: ExpressionDefinition[ObjectWithMethodDef],
                           protected val services: Map[String, ObjectWithMethodDef]) {

  type ParametersProviderT = ObjectWithMethodDef

  private val syntax = ValidatedSyntax[ProcessCompilationError]

  import CompilationResult._
  import syntax._

  //FIXME: should it be here?
  private val expressionEvaluator =
    ExpressionEvaluator.withoutLazyVals(GlobalVariablesPreparer(expressionConfig), List.empty)

  def validate(n: splittednode.SplittedNode[_], ctx: ValidationContext): CompilationResult[Unit] = {
    compile(n, ctx).map(_ => ())
  }

  protected def createServiceInvoker(obj: ObjectWithMethodDef) = ServiceInvoker(obj)

  /* TODO:
  1. Separate validation logic for expressions in nodes and expression not bounded to nodes (e.g. expressions in process properties).
     This way we can make non-optional fieldName
   */
  def compile(n: SplittedNode[_], ctx: ValidationContext) : CompilationResult[compiledgraph.node.Node] = {
    implicit val nodeId: NodeId = NodeId(n.id)

    def toCompilationResult[T](validated: ValidatedNel[ProcessCompilationError, T], expressionsTypingInfo: Map[String, ExpressionTypingInfo]) =
      CompilationResult(Map(n.id -> NodeTypingInfo(ctx, expressionsTypingInfo)), validated)

    n match {
      case splittednode.SourceNode(nodeData, next) => handleSourceNode(nodeData, ctx, next)
      case splittednode.OneOutputSubsequentNode(data, next) => compileSubsequent(ctx, data, next)

      case splittednode.SplitNode(bareNode, nexts) =>
        val compiledNexts = nexts.map(n => compile(n, ctx)).sequence
        compiledNexts.andThen(nx => toCompilationResult(Valid(compiledgraph.node.SplitNode(bareNode.id, nx)), Map.empty))

      case splittednode.FilterNode(f@graph.node.Filter(id, expression, _, _), nextTrue, nextFalse) =>
        val (expressionTyping, validatedExpression) = compile(expression, Some(DefaultExpressionId), ctx, Typed[Boolean])
        CompilationResult.map3(toCompilationResult(validatedExpression, expressionTyping.toDefaultExpressionTypingInfoEntry.toMap), compile(nextTrue, ctx), nextFalse.map(next => compile(next, ctx)).sequence)(
          (expr, next, nextFalse) =>
            compiledgraph.node.Filter(id = id,
            expression = expr,
            nextTrue = next,
            nextFalse = nextFalse,
            isDisabled = f.isDisabled.contains(true)))

      case splittednode.SwitchNode(graph.node.Switch(id, expression, exprVal, _), nexts, defaultNext) =>
        val (expressionTyping, validatedExpression) = compile(expression, Some(DefaultExpressionId), ctx, Unknown)
        val (newCtx, combinedValidatedExpression) = withVariableCombined(ctx, exprVal, expressionTyping.typingResult, validatedExpression)
        CompilationResult.map3(toCompilationResult(combinedValidatedExpression, expressionTyping.toDefaultExpressionTypingInfoEntry.toMap), nexts.map(n => compile(n, newCtx)).sequence, defaultNext.map(dn => compile(dn, newCtx)).sequence)(
          (realCompiledExpression, cases, next) => {
            compiledgraph.node.Switch(id, realCompiledExpression, exprVal, cases, next)
          })
      case splittednode.EndingNode(data) => compileEndingNode(ctx, data)

    }
  }

  private def handleSourceNode(nodeData: StartingNodeData, ctx: ValidationContext, next: splittednode.Next): CompilationResult[node.Source] = {
    // just like in a custom node we can't add input context here because it contains output variable context (not input)
    nodeData match {
      case graph.node.Source(id, _, _) =>
        compile(next, ctx).map(nwc => compiledgraph.node.Source(id, nwc))
      case graph.node.Join(id, _, _, _, _, _) =>
        compile(next, ctx).map(nwc => compiledgraph.node.Source(id, nwc))
      case SubprocessInputDefinition(id, _, _) =>
        //TODO: should we recognize we're compiling only subprocess?
        compile(next, ctx).map(nwc => compiledgraph.node.Source(id, nwc))
    }
  }

  private def compileEndingNode(ctx: ValidationContext, data: EndingNodeData)(implicit nodeId: NodeId): CompilationResult[compiledgraph.node.Node] = {
    def toCompilationResult[T](validated: ValidatedNel[ProcessCompilationError, T], expressionsTypingInfo: Map[String, ExpressionTypingInfo]) =
      CompilationResult(Map(nodeId.id -> NodeTypingInfo(ctx, expressionsTypingInfo)), validated)

    data match {
      case graph.node.Processor(id, ref, disabled, _) =>
        val (typingResult, validatedServiceRef) = compile(ref, ctx)
        toCompilationResult(validatedServiceRef.map(ref => compiledgraph.node.EndingProcessor(id, ref, disabled.contains(true))), typingResult.expressionsTypingInfo)
      case graph.node.Sink(id, ref, optionalExpression, disabled, _) =>
        val (expressionTypingInfoEntry, validatedOptionalExpression) = optionalExpression.map { oe =>
          val (expressionTyping, validatedExpression) = compile(oe, Some(DefaultExpressionId), ctx, Unknown)
          (expressionTyping.toDefaultExpressionTypingInfoEntry, validatedExpression.map(expr => Some((expr, expressionTyping.typingResult))))
        }.getOrElse {
          (None, Valid(None))
        }

        toCompilationResult(validatedOptionalExpression.map(compiledgraph.node.Sink(id, ref.typ, _, disabled.contains(true))), expressionTypingInfoEntry.toMap)
      //probably this shouldn't occur - otherwise we'd have empty subprocess?
      case SubprocessInput(id, _, _, _, _) => toCompilationResult(Invalid(NonEmptyList.of(UnresolvedSubprocess(id))), Map.empty)
      case SubprocessOutputDefinition(id, name, _) =>
        //TODO: should we validate it's process?
        //TODO: does it make sense to validate SubprocessOutput?
        toCompilationResult(Valid(compiledgraph.node.Sink(id, name, None, isDisabled = false)), Map.empty)

      //TODO JOIN: a lot of additional validations needed here - e.g. that join with that name exists, that it
      //accepts this join, maybe we should also validate the graph is connected?
      case BranchEndData(definition) => toCompilationResult(Valid(compiledgraph.node.BranchEnd(definition)), Map.empty)
    }
  }

  private def compileSubsequent(ctx: ValidationContext, data: OneOutputSubsequentNodeData, next: Next)(implicit nodeId: NodeId): CompilationResult[Node] = {
    def toCompilationResult[T](validated: ValidatedNel[ProcessCompilationError, T], expressionsTypingInfo: Map[String, ExpressionTypingInfo]) =
      CompilationResult(Map(data.id -> NodeTypingInfo(ctx, expressionsTypingInfo)), validated)

    data match {
      case graph.node.Variable(id, varName, expression, _) =>
        val (expressionTypingResult, validatedExpression) = compile(expression, Some(DefaultExpressionId), ctx, Unknown)
        val (newCtx, combinedValidatedExpression) = withVariableCombined(ctx, varName, expressionTypingResult.typingResult, validatedExpression)

        CompilationResult.map2(toCompilationResult(combinedValidatedExpression, expressionTypingResult.toDefaultExpressionTypingInfoEntry.toMap), compile(next, newCtx)) { (compiled, compiledNext) =>
          compiledgraph.node.VariableBuilder(id, varName, Left(compiled), compiledNext)
        }
      case graph.node.VariableBuilder(id, varName, fields, _) =>
        val (fieldsTyping, compiledFields) = fields.map(f => compile(f, ctx)).unzip
        val typingResult = TypedObjectTypingResult(fieldsTyping.map(f => f.fieldName -> f.typingResult).toMap)
        val (newCtx, combinedCompiledFields) = withVariableCombined(ctx, varName, typingResult, compiledFields.sequence)

        val expressionsTypingInfo = fieldsTyping.flatMap(_.toExpressionTypingInfoEntry).toMap

        CompilationResult.map2(toCompilationResult(combinedCompiledFields, expressionsTypingInfo), compile(next, newCtx)) { (compiledFields, compiledNext) =>
          compiledgraph.node.VariableBuilder(id, varName, Right(compiledFields), compiledNext)
        }

      case graph.node.Processor(id, ref, isDisabled, _) =>
        val (typingResult, validatedServiceRef) = compile(ref, ctx)
        CompilationResult.map2(toCompilationResult(validatedServiceRef, typingResult.expressionsTypingInfo), compile(next, ctx))((ref, next) =>
          compiledgraph.node.Processor(id, ref, next, isDisabled.contains(true)))

      case graph.node.Enricher(id, ref, outName, _) =>
        val (typingResult, validatedServiceRef) = compile(ref, ctx)

        val (newCtx, combinedValidatedServiceRef) = withVariableCombined(ctx, outName, typingResult.returnType, validatedServiceRef)
        CompilationResult.map2(toCompilationResult(combinedValidatedServiceRef, typingResult.expressionsTypingInfo), compile(next, newCtx))((ref, next) =>
                           compiledgraph.node.Enricher(id, ref, outName, next))

      //here we don't do anything, in subgraphcompiler it's just pass through, we can't add input context here because it contains output variable context (not input)
      case graph.node.CustomNode(id, _, _, _, _) =>
        CompilationResult.map(
          fa = compile(next, ctx))(
          f = compiledNext => compiledgraph.node.CustomNode(id, compiledNext))

      case subprocessInput@SubprocessInput(id, ref, _, _, _) =>
        import cats.implicits.toTraverseOps

        val childCtx = ctx.pushNewContext()
        val (newCtx, combinedValidation) = ref.parameters.foldLeft[(ValidationContext, ValidatedNel[ProcessCompilationError, _])]((childCtx, Valid(Unit))) {
          case ((accCtx, validation), param) =>
            withVariableCombined(accCtx, param.name, Unknown, validation)
        }

        val validParamDefs =
          ref.parameters.map(p => getSubprocessParamDefinition(subprocessInput, p.name)).sequence

        val validParams = validParamDefs.andThen { paramDefs =>
          expressionCompiler.compileEagerObjectParameters(paramDefs, ref.parameters, ctx)
        }

        val expressionTypingInfo = validParams.map(_.map(p => p.name -> p.typingInfo).toMap).valueOr(_ => Map.empty[String, ExpressionTypingInfo])

        val combinedValidParams = ProcessCompilationError.ValidatedNelApplicative.map2(combinedValidation, validParams)((_, p) => p)

        CompilationResult.map2(toCompilationResult(combinedValidParams, expressionTypingInfo), compile(next, newCtx))((params, next) =>
          compiledgraph.node.SubprocessStart(id, params, next))

      case SubprocessOutput(id, _, _) =>
        //this popContext *really* has to work to be able to extract variable types :|
        ctx.popContext
          .map(popContext => compile(next, popContext).andThen(next => toCompilationResult(Valid(SubprocessEnd(id, next)), Map.empty)))
          .valueOr(error => CompilationResult(Invalid(error)))
    }
  }

  private def compile(next: splittednode.Next, ctx: ValidationContext): CompilationResult[compiledgraph.node.Next] = {
    next match {
      case splittednode.NextNode(n) => compile(n, ctx).map(cn => compiledgraph.node.NextNode(cn))
      case splittednode.PartRef(ref) =>
        CompilationResult(Map(ref -> NodeTypingInfo(ctx, Map.empty)), Valid(compiledgraph.node.PartRef(ref)))
    }
  }

  private def compile(n: graph.service.ServiceRef, ctx: ValidationContext)
                     (implicit nodeId: NodeId): (ServiceTypingResult, ValidatedNel[ProcessCompilationError, compiledgraph.service.ServiceRef]) = {
    val service = services.get(n.id).map(Valid(_)).getOrElse(invalid(MissingService(n.id))).toValidatedNel

    val validatedServiceWithTypingResult = service.andThen { objWithMethod =>
      expressionCompiler.compileEagerObjectParameters(objWithMethod.parameters, n.parameters, ctx).map { params =>
        val invoker = createServiceInvoker(objWithMethod)
        val returnType = computeReturnType(objWithMethod, params)
        val typingResult = ServiceTypingResult(returnType, params.map(p => p.name -> p.typingInfo).toMap)
        (compiledgraph.service.ServiceRef(n.id, invoker, params), typingResult)
      }
    }
    validatedServiceWithTypingResult match {
      case Valid((serviceRef, typingResult)) =>
        (typingResult, Valid(serviceRef))
      case invalid@Invalid(_) =>
        (ServiceTypingResult(Unknown, Map.empty), invalid)
    }
  }

  private def withVariableCombined[R](validationContext: ValidationContext, variableName: String, typingResult: TypingResult,
                                      validatedResult: ValidatedNel[ProcessCompilationError, R])(implicit nodeId: NodeId)
    : (ValidationContext, ValidatedNel[ProcessCompilationError, R]) = {
    val combinedValidationWithNewCtx = ProcessCompilationError.ValidatedNelApplicative.product(validationContext.withVariable(variableName, typingResult), validatedResult)
    (combinedValidationWithNewCtx.map(_._1).valueOr(_ => validationContext), combinedValidationWithNewCtx.map(_._2))
  }

  private def compile(n: splittednode.Case, ctx: ValidationContext)
                     (implicit nodeId: NodeId): CompilationResult[compiledgraph.node.Case] =
    CompilationResult.map2(CompilationResult(compile(n.expression, Some(DefaultExpressionId), ctx, Typed[Boolean])._2), compile(n.node, ctx))((expr, next) => compiledgraph.node.Case(expr, next))


  private def compile(field: graph.variable.Field, ctx: ValidationContext)
                     (implicit nodeId: NodeId): (FieldExpressionTypingResult, ValidatedNel[ProcessCompilationError, compiledgraph.variable.Field]) = {
    val (expressionTyping, validatedExpression) = compile(field.expression, Some(field.name), ctx, Unknown)
    (FieldExpressionTypingResult(field.name, expressionTyping), validatedExpression.map(compiledgraph.variable.Field(field.name, _)))
  }

  private def compile(n: graph.expression.Expression,
                      fieldName: Option[String],
                      ctx: ValidationContext,
                      expectedType: TypingResult)
                     (implicit nodeId: NodeId): (ExpressionTypingResult, ValidatedNel[ProcessCompilationError, api.expression.Expression]) = {
    expressionCompiler.compile(n, fieldName, ctx, expectedType)
      .map(res => (ExpressionTypingResult(res.returnType, Some(res.typingInfo)), Valid(res.expression)))
      .valueOr(err => (ExpressionTypingResult(Unknown, None), Invalid(err)))
  }

  //this method tries to compute constant parameters if service is ServiceReturningType
  //TODO: is it right way to do this? Maybe we just need to analyze Expression?
  private def computeReturnType(objWithMethod: ObjectWithMethodDef,
                                parameters: List[compiledgraph.evaluatedparam.Parameter]): TypingResult = objWithMethod.obj match {
    case srt: ServiceReturningType =>

      val data = parameters.map { param =>
        param.name -> (param.returnType, tryToEvaluateParam(param))
      }.toMap
      srt.returnType(data)
    case _ =>
      objWithMethod.returnType
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
    subParam.typ.toRuntimeClass(classLoader) match {
      case Success(runtimeClass) =>
        valid(Parameter(paramName, Typed(runtimeClass), runtimeClass))
      case Failure(_) =>
        invalid(
          SubprocessParamClassLoadError(paramName, subParam.typ.refClazzName, subprocessInput.id)
        ).toValidatedNel
    }
  }


  def withExpressionParsers(modify: PartialFunction[ExpressionParser, ExpressionParser]): PartSubGraphCompiler =
    new PartSubGraphCompiler(classLoader, expressionCompiler.withExpressionParsers(modify), expressionConfig, services)

}

object PartSubGraphCompiler {

  private case class ExpressionTypingResult(typingResult: TypingResult, typingInfo: Option[ExpressionTypingInfo]) {

    def toDefaultExpressionTypingInfoEntry: Option[(String, ExpressionTypingInfo)] =
      typingInfo.map(NodeTypingInfo.DefaultExpressionId -> _)

  }

  private case class FieldExpressionTypingResult(fieldName: String, private val exprTypingResult: ExpressionTypingResult) {

    def typingResult: TypingResult = exprTypingResult.typingResult

    def toExpressionTypingInfoEntry: Option[(String, ExpressionTypingInfo)] =
      exprTypingResult.typingInfo.map(fieldName -> _)

  }

  private case class ServiceTypingResult(returnType: TypingResult, expressionsTypingInfo: Map[String, ExpressionTypingInfo])

}