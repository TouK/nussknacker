package pl.touk.nussknacker.engine.compile

import cats.data.Validated._
import cats.data.{NonEmptyList, ValidatedNel}
import cats.instances.list._
import cats.instances.option._
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context.{OutputVar, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.expression.{ExpressionParser, ExpressionTypingInfo}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.NodeCompilationResult
import pl.touk.nussknacker.engine.compiledgraph
import pl.touk.nussknacker.engine.compiledgraph.node
import pl.touk.nussknacker.engine.compiledgraph.node.{Node, SubprocessEnd}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import pl.touk.nussknacker.engine.splittedgraph._
import pl.touk.nussknacker.engine.splittedgraph.splittednode.{Next, SplittedNode}

class PartSubGraphCompiler(expressionCompiler: ExpressionCompiler,
                           nodeCompiler: NodeCompiler) {

  type ParametersProviderT = ObjectWithMethodDef

  import CompilationResult._

  def validate(n: splittednode.SplittedNode[_], ctx: ValidationContext)(implicit metaData: MetaData): CompilationResult[Unit] = {
    compile(n, ctx).map(_ => ())
  }

  /* TODO:
  1. Separate validation logic for expressions in nodes and expression not bounded to nodes (e.g. expressions in process properties).
     This way we can make non-optional fieldName
   */
  def compile(n: SplittedNode[_], ctx: ValidationContext)(implicit metaData: MetaData): CompilationResult[compiledgraph.node.Node] = {
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

      case splittednode.FilterNode(f@Filter(id, expression, _, _), nextTrue, nextFalse) =>
        val NodeCompilationResult(typingInfo, _, _, compiledExpression, _) =
          nodeCompiler.compileExpression(expression, ctx, expectedType = Typed[Boolean], outputVar = None)
        CompilationResult.map3(
          f0 = toCompilationResult(compiledExpression, typingInfo),
          f1 = compile(nextTrue, ctx),
          f2 = nextFalse.map(next => compile(next, ctx)).sequence)(
          (expr, next, nextFalse) =>
            compiledgraph.node.Filter(id = id,
              expression = expr,
              nextTrue = next,
              nextFalse = nextFalse,
              isDisabled = f.isDisabled.contains(true)))

      case splittednode.SwitchNode(Switch(id, expression, varName, _), nexts, defaultNext) =>
        val NodeCompilationResult(expressionTyping, _, newCtxValidated, compiledExpression, _) =
          nodeCompiler.compileExpression(expression, ctx, expectedType = Unknown, outputVar = Some(OutputVar.switch(varName)))
        val newCtx = newCtxValidated.getOrElse(ctx)
        CompilationResult.map4(
          f0 = CompilationResult(newCtxValidated),
          f1 = toCompilationResult(compiledExpression, expressionTyping),
          f2 = nexts.map(caseNode => compile(caseNode, newCtx)).sequence,
          f3 = defaultNext.map(dn => compile(dn, newCtx)).sequence)(
          (_, realCompiledExpression, cases, next) => {
            compiledgraph.node.Switch(id, realCompiledExpression, varName, cases, next)
          })
      case splittednode.EndingNode(data) => compileEndingNode(ctx, data)

    }
  }

  private def handleSourceNode(nodeData: StartingNodeData, ctx: ValidationContext, next: splittednode.Next)
                              (implicit metaData: MetaData): CompilationResult[node.Source] = {
    // just like in a custom node we can't add input context here because it contains output variable context (not input)
    nodeData match {
      case Source(id, _, _) =>
        compile(next, ctx).map(nwc => compiledgraph.node.Source(id, nwc))
      case Join(id, _, _, _, _, _) =>
        compile(next, ctx).map(nwc => compiledgraph.node.Source(id, nwc))
      case SubprocessInputDefinition(id, _, _) =>
        //TODO: should we recognize we're compiling only subprocess?
        compile(next, ctx).map(nwc => compiledgraph.node.Source(id, nwc))
    }
  }

  private def compileEndingNode(ctx: ValidationContext, data: EndingNodeData)(implicit nodeId: NodeId, metaData: MetaData): CompilationResult[compiledgraph.node.Node] = {
    def toCompilationResult[T](validated: ValidatedNel[ProcessCompilationError, T], expressionsTypingInfo: Map[String, ExpressionTypingInfo]) =
      CompilationResult(Map(nodeId.id -> NodeTypingInfo(ctx, expressionsTypingInfo, None)), validated)

    data match {
      case processor@Processor(id, _, disabled, _) =>
        val NodeCompilationResult(typingInfo, _, _, validatedServiceRef, _) = nodeCompiler.compileProcessor(processor, ctx)
        toCompilationResult(validatedServiceRef.map(ref => compiledgraph.node.EndingProcessor(id, ref, disabled.contains(true))), typingInfo)

      case Sink(id, ref, _, disabled, _) =>
        toCompilationResult(Valid(compiledgraph.node.Sink(id, ref.typ, disabled.contains(true))), Map.empty)

      case CustomNode(id, _, _, _, _) =>
        toCompilationResult(Valid(compiledgraph.node.EndingCustomNode(id)), Map.empty)

      //probably this shouldn't occur - otherwise we'd have empty subprocess?
      case SubprocessInput(id, _, _, _, _) => toCompilationResult(Invalid(NonEmptyList.of(UnresolvedSubprocess(id))), Map.empty)

      case SubprocessOutputDefinition(id, outputName, List(), _) =>
        //TODO: should we validate it's process?
        //TODO: does it make sense to validate SubprocessOutput?
        toCompilationResult(Valid(compiledgraph.node.Sink(id, outputName, isDisabled = false)), Map.empty)
      case SubprocessOutputDefinition(id, outputName, fields, _) =>
        val NodeCompilationResult(typingInfo, _, ctxV, compiledFields, _) =
          nodeCompiler.compileFields(fields, ctx, outputVar = Some(OutputVar.subprocess(outputName)))
        CompilationResult.map2(
          fa = CompilationResult(ctxV),
          fb = toCompilationResult(compiledFields, typingInfo)
        ) { (_, _) =>
          compiledgraph.node.Sink(id, outputName, isDisabled = false)
        }

      //TODO JOIN: a lot of additional validations needed here - e.g. that join with that name exists, that it
      //accepts this join, maybe we should also validate the graph is connected?
      case BranchEndData(definition) => toCompilationResult(Valid(compiledgraph.node.BranchEnd(definition)), Map.empty)
    }
  }

  private def compileSubsequent(ctx: ValidationContext, data: OneOutputSubsequentNodeData, next: Next)
                               (implicit nodeId: NodeId, metaData: MetaData): CompilationResult[Node] = {
    def toCompilationResult[T](validated: ValidatedNel[ProcessCompilationError, T],
                               expressionsTypingInfo: Map[String, ExpressionTypingInfo], parameters: Option[List[Parameter]]) =
      CompilationResult(Map(data.id -> NodeTypingInfo(ctx, expressionsTypingInfo, parameters)), validated)

    data match {
      case Variable(id, varName, expression, _) =>
        val NodeCompilationResult(typingInfo, parameters, newCtx, compiledExpression, t) =
          nodeCompiler.compileExpression(expression, ctx, expectedType = Unknown, outputVar = Some(OutputVar.variable(varName)))
        CompilationResult.map3(
          f0 = CompilationResult(newCtx),
          f1 = toCompilationResult(compiledExpression, typingInfo, parameters),
          f2 = compile(next, newCtx.getOrElse(ctx))) {
          (_, compiled, compiledNext) =>
            compiledgraph.node.VariableBuilder(id, varName, Left(compiled), compiledNext)
        }
      case VariableBuilder(id, varName, fields, _) =>
        val NodeCompilationResult(typingInfo, parameters, newCtxV, compiledFields, _) =
          nodeCompiler.compileFields(fields, ctx, outputVar = Some(OutputVar.variable(varName)))
        CompilationResult.map3(
          f0 = CompilationResult(newCtxV),
          f1 = toCompilationResult(compiledFields, typingInfo, parameters),
          f2 = compile(next, newCtxV.getOrElse(ctx))) {
          (_, compiledFields, compiledNext) =>
            compiledgraph.node.VariableBuilder(id, varName, Right(compiledFields), compiledNext)
        }

      case processor@Processor(id, _, isDisabled, _) =>
        val NodeCompilationResult(typingInfo, parameters, _, validatedServiceRef, _) = nodeCompiler.compileProcessor(processor, ctx)
        CompilationResult.map2(toCompilationResult(validatedServiceRef, typingInfo, parameters), compile(next, ctx))((ref, next) =>
          compiledgraph.node.Processor(id, ref, next, isDisabled.contains(true)))

      case enricher@Enricher(id, _, output, _) =>
        val NodeCompilationResult(typingInfo, parameters, newCtx, validatedServiceRef, _) = nodeCompiler.compileEnricher(enricher, ctx, outputVar = Some(OutputVar.enricher(output)))

        CompilationResult.map3(
          toCompilationResult(validatedServiceRef, typingInfo, parameters),
          CompilationResult(newCtx),
          compile(next, newCtx.getOrElse(ctx)))((ref, _, next) => compiledgraph.node.Enricher(id, ref, output, next))

      //here we don't do anything, in subgraphcompiler it's just pass through, we can't add input context here because it contains output variable context (not input)
      case CustomNode(id, _, _, _, _) =>
        CompilationResult.map(
          fa = compile(next, ctx))(
          f = compiledNext => compiledgraph.node.CustomNode(id, compiledNext))

      case subprocessInput: SubprocessInput =>
        val NodeCompilationResult(typingInfo, parameters, newCtx, combinedValidParams, _) = nodeCompiler.compileSubprocessInput(subprocessInput, ctx)
        CompilationResult.map2(toCompilationResult(combinedValidParams, typingInfo, parameters), compile(next, newCtx.getOrElse(ctx)))((params, next) =>
          compiledgraph.node.SubprocessStart(subprocessInput.id, params, next))

      case SubprocessOutput(id, outPutName, List(), _) =>
        // Missing 'parent context' means that fragment has used some component which cleared context. We compile next parts using new, empty context.
        val parentContext = ctx.popContextOrEmpty()
        compile(next, parentContext)
          .andThen(compiledNext => toCompilationResult(Valid(SubprocessEnd(id, outPutName, List(), compiledNext)), Map.empty, None))
      case SubprocessOutput(id, outputName, fields, _) =>
        val NodeCompilationResult(typingInfo, parameters, ctxWithSubOutV, compiledFields, typingResult) =
          nodeCompiler.compileFields(fields, ctx, outputVar = Some(OutputVar.subprocess(outputName)))
        // Missing 'parent context' means that fragment has used some component which cleared context. We compile next parts using new, empty context.
        val parentCtx = ctx.popContextOrEmpty()
        val parentCtxWithSubOut = parentCtx
          .withVariable(OutputVar.subprocess(outputName), typingResult.getOrElse(Unknown))
          .getOrElse(parentCtx)
        CompilationResult.map3(
          f0 = CompilationResult(ctxWithSubOutV),
          f1 = toCompilationResult(compiledFields, typingInfo, parameters),
          f2 = compile(next, parentCtxWithSubOut)) {
          (_, compiledFields, compiledNext) =>
            compiledgraph.node.SubprocessEnd(id, outputName, compiledFields, compiledNext)
        }
    }
  }

  private def compile(next: splittednode.Next, ctx: ValidationContext)(implicit metaData: MetaData): CompilationResult[compiledgraph.node.Next] = {
    next match {
      case splittednode.NextNode(n) => compile(n, ctx).map(cn => compiledgraph.node.NextNode(cn))
      case splittednode.PartRef(ref) =>
        CompilationResult(Map(ref -> NodeTypingInfo(ctx, Map.empty, None)), Valid(compiledgraph.node.PartRef(ref)))
    }
  }

  private def compile(n: splittednode.Case, ctx: ValidationContext)
                     (implicit nodeId: NodeId, metaData: MetaData): CompilationResult[compiledgraph.node.Case] =
    CompilationResult.map2(
      fa = CompilationResult(nodeCompiler.compileExpression(n.expression, ctx, Typed[Boolean], outputVar = None).compiledObject),
      fb = compile(n.node, ctx)) {
      (expr, next) => compiledgraph.node.Case(expr, next)
    }

  def withExpressionParsers(modify: PartialFunction[ExpressionParser, ExpressionParser]): PartSubGraphCompiler =
    new PartSubGraphCompiler(expressionCompiler.withExpressionParsers(modify), nodeCompiler.withExpressionParsers(modify))
}

