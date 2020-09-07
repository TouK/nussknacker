package pl.touk.nussknacker.engine.compile

import cats.data.Validated._
import cats.data.{NonEmptyList, ValidatedNel}
import cats.instances.list._
import cats.instances.option._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.expression.{ExpressionParser, ExpressionTypingInfo}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.compile.NodeTypingInfo.DefaultExpressionId
import pl.touk.nussknacker.engine.compile.PartSubGraphCompiler._
import pl.touk.nussknacker.engine.compile.nodecompilation.{NodeCompilationResult, NodeCompiler}
import pl.touk.nussknacker.engine.compiledgraph.node
import pl.touk.nussknacker.engine.compiledgraph.node.{Node, SubprocessEnd}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.splittedgraph._
import pl.touk.nussknacker.engine.splittedgraph.splittednode.{Next, SplittedNode}
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax
import pl.touk.nussknacker.engine.{api, compiledgraph, _}

class PartSubGraphCompiler(expressionCompiler: ExpressionCompiler,
                           nodeCompiler: NodeCompiler) {

  type ParametersProviderT = ObjectWithMethodDef

  private val syntax = ValidatedSyntax[ProcessCompilationError]

  import CompilationResult._
  import syntax._

  def validate(n: splittednode.SplittedNode[_], ctx: ValidationContext): CompilationResult[Unit] = {
    compile(n, ctx).map(_ => ())
  }

  /* TODO:
  1. Separate validation logic for expressions in nodes and expression not bounded to nodes (e.g. expressions in process properties).
     This way we can make non-optional fieldName
   */
  def compile(n: SplittedNode[_], ctx: ValidationContext) : CompilationResult[compiledgraph.node.Node] = {
    implicit val nodeId: NodeId = NodeId(n.id)

    def toCompilationResult[T](validated: ValidatedNel[ProcessCompilationError, T],
                               expressionsTypingInfo: Map[String, ExpressionTypingInfo]) =
      CompilationResult(Map(n.id -> NodeTypingInfo(ctx, expressionsTypingInfo, None)), validated)

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
      CompilationResult(Map(nodeId.id -> NodeTypingInfo(ctx, expressionsTypingInfo, None)), validated)

    data match {
      case processor@graph.node.Processor(id, _, disabled, _) =>
        val NodeCompilationResult(typingInfo, _, _, validatedServiceRef, _) = nodeCompiler.compileProcessor(processor, ctx)
        toCompilationResult(validatedServiceRef.map(ref => compiledgraph.node.EndingProcessor(id, ref, disabled.contains(true))), typingInfo)

      case graph.node.Sink(id, ref, optionalExpression, disabled, _) =>
        val (expressionTypingInfoEntry, validatedOptionalExpression) = optionalExpression.map { oe =>
          val (expressionTyping, validatedExpression) = compile(oe, Some(DefaultExpressionId), ctx, Unknown)
          (expressionTyping.toDefaultExpressionTypingInfoEntry, validatedExpression.map(expr => Some((expr, expressionTyping.typingResult))))
        }.getOrElse {
          (None, Valid(None))
        }
        toCompilationResult(validatedOptionalExpression.map(compiledgraph.node.Sink(id, ref.typ, _, disabled.contains(true))), expressionTypingInfoEntry.toMap)

      case graph.node.CustomNode(id, _, _, _, _) =>
        toCompilationResult(Valid(compiledgraph.node.EndingCustomNode(id)), Map.empty)

      //probably this shouldn't occur - otherwise we'd have empty subprocess?
      case SubprocessInput(id, _, _, _, _) => toCompilationResult(Invalid(NonEmptyList.of(UnresolvedSubprocess(id))), Map.empty)

      case SubprocessOutputDefinition(id, outputName, List(), _) =>
        //TODO: should we validate it's process?
        //TODO: does it make sense to validate SubprocessOutput?
        toCompilationResult(Valid(compiledgraph.node.Sink(id, outputName, None, isDisabled = false)), Map.empty)
      case SubprocessOutputDefinition(id, outputName, fields, _) =>
        val (fieldsTyping, compiledFields) = fields.map(f => compile(f, ctx)).unzip
        val typingResult = TypedObjectTypingResult(fieldsTyping.map(f => f.fieldName -> f.typingResult).toMap)
        val (_, combinedCompiledFields) = withVariableCombined(ctx, outputName, typingResult, compiledFields.sequence)
        val expressionsTypingInfo = fieldsTyping.flatMap(_.toExpressionTypingInfoEntry).toMap
        toCompilationResult(combinedCompiledFields, expressionsTypingInfo).map { _ =>
          compiledgraph.node.Sink(id, outputName, None, isDisabled = false)
        }

      //TODO JOIN: a lot of additional validations needed here - e.g. that join with that name exists, that it
      //accepts this join, maybe we should also validate the graph is connected?
      case BranchEndData(definition) => toCompilationResult(Valid(compiledgraph.node.BranchEnd(definition)), Map.empty)
    }
  }

  private def compileSubsequent(ctx: ValidationContext, data: OneOutputSubsequentNodeData, next: Next)(implicit nodeId: NodeId): CompilationResult[Node] = {
    def toCompilationResult[T](validated: ValidatedNel[ProcessCompilationError, T], expressionsTypingInfo: Map[String, ExpressionTypingInfo]) =
      CompilationResult(Map(data.id -> NodeTypingInfo(ctx, expressionsTypingInfo, None)), validated)

    data match {
      case graph.node.Variable(id, varName, expression, _) =>
        val NodeCompilationResult(typingInfo, _, newValidatedCtx, validatedExpression, _) =
          nodeCompiler.compileExpression(expression, varName, ctx)

        CompilationResult.map2(
          fa = toCompilationResult(validatedExpression, typingInfo),
          fb = compile(next, newValidatedCtx.getOrElse(ctx))) {
          (compiled, compiledNext) => compiledgraph.node.VariableBuilder(id, varName, Left(compiled), compiledNext)
        }
      case graph.node.VariableBuilder(id, varName, fields, _) =>
        val NodeCompilationResult(typingInfo, _, newValidatedCtx, validatedFields, _) =
          nodeCompiler.compileFields(fields, varName, ctx)

        CompilationResult.map2(
          fa = toCompilationResult(validatedFields, typingInfo),
          fb = compile(next, newValidatedCtx.getOrElse(ctx))
        ) {
          (compiled, compiledNext) => compiledgraph.node.VariableBuilder(id, varName, Right(compiled), compiledNext)
        }

      case processor@graph.node.Processor(id, _, isDisabled, _) =>
        val NodeCompilationResult(typingInfo, _, _, validatedServiceRef, _) = nodeCompiler.compileProcessor(processor, ctx)
        CompilationResult.map2(toCompilationResult(validatedServiceRef, typingInfo), compile(next, ctx))((ref, next) =>
          compiledgraph.node.Processor(id, ref, next, isDisabled.contains(true)))

      case enricher@graph.node.Enricher(id, _, outName, _) =>
        val NodeCompilationResult(typingInfo, _, newCtx, validatedServiceRef, _) = nodeCompiler.compileEnricher(enricher, ctx)

        CompilationResult.map3(
          toCompilationResult(validatedServiceRef, typingInfo),
          CompilationResult(newCtx),
          compile(next, newCtx.getOrElse(ctx)))((ref, _, next) => compiledgraph.node.Enricher(id, ref, outName, next))

      //here we don't do anything, in subgraphcompiler it's just pass through, we can't add input context here because it contains output variable context (not input)
      case graph.node.CustomNode(id, _, _, _, _) =>
        CompilationResult.map(
          fa = compile(next, ctx))(
          f = compiledNext => compiledgraph.node.CustomNode(id, compiledNext))

      case subprocessInput:SubprocessInput =>
        val NodeCompilationResult(typingInfo, _, newCtx, combinedValidParams, _) = nodeCompiler.compileSubprocessInput(subprocessInput, ctx)
        CompilationResult.map2(toCompilationResult(combinedValidParams, typingInfo), compile(next, newCtx.getOrElse(ctx)))((params, next) =>
          compiledgraph.node.SubprocessStart(subprocessInput.id, params, next))

      case SubprocessOutput(id, outPutName, List(), _) =>
        //this popContext *really* has to work to be able to extract variable types :|
        ctx.popContext
          .map(popContext => compile(next, popContext).andThen(next => toCompilationResult(Valid(SubprocessEnd(id, outPutName, List(), next)), Map.empty)))
          .valueOr(error => CompilationResult(Invalid(error)))
      case SubprocessOutput(id, outputName, fields, _) =>
        ctx.popContext.map { parentCtx =>
          val (fieldsTyping, compiledFields) = fields.map(f => compile(f, ctx)).unzip
          val typingResult = TypedObjectTypingResult(fieldsTyping.map(f => f.fieldName -> f.typingResult).toMap)
          val (parentCtxWithSubOut, combinedCompiledFields) = withVariableCombined(parentCtx, outputName, typingResult, compiledFields.sequence)
          val expressionsTypingInfo = fieldsTyping.flatMap(_.toExpressionTypingInfoEntry).toMap
          CompilationResult.map2(toCompilationResult(combinedCompiledFields, expressionsTypingInfo), compile(next, parentCtxWithSubOut)) { (compiledFields, compiledNext) =>
            compiledgraph.node.SubprocessEnd(id, outputName, compiledFields, compiledNext)
          }
        }.valueOr(error => CompilationResult(Invalid(error)))
    }
  }

  private def compile(next: splittednode.Next, ctx: ValidationContext): CompilationResult[compiledgraph.node.Next] = {
    next match {
      case splittednode.NextNode(n) => compile(n, ctx).map(cn => compiledgraph.node.NextNode(cn))
      case splittednode.PartRef(ref) =>
        CompilationResult(Map(ref -> NodeTypingInfo(ctx, Map.empty, None)), Valid(compiledgraph.node.PartRef(ref)))
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

  def withExpressionParsers(modify: PartialFunction[ExpressionParser, ExpressionParser]): PartSubGraphCompiler =
    new PartSubGraphCompiler(expressionCompiler.withExpressionParsers(modify), nodeCompiler.withExpressionParsers(modify))

}

object PartSubGraphCompiler {

  case class ExpressionTypingResult(typingResult: TypingResult, typingInfo: Option[ExpressionTypingInfo]) {

    def toDefaultExpressionTypingInfoEntry: Option[(String, ExpressionTypingInfo)] =
      typingInfo.map(NodeTypingInfo.DefaultExpressionId -> _)

  }

  case class FieldExpressionTypingResult(fieldName: String, private val exprTypingResult: ExpressionTypingResult) {

    def typingResult: TypingResult = exprTypingResult.typingResult

    def toExpressionTypingInfoEntry: Option[(String, ExpressionTypingInfo)] =
      exprTypingResult.typingInfo.map(fieldName -> _)

  }

  private case class ServiceTypingResult(returnType: TypingResult, expressionsTypingInfo: Map[String, ExpressionTypingInfo])

}
