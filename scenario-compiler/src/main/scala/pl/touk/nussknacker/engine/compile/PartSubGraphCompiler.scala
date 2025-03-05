package pl.touk.nussknacker.engine.compile

import cats.Applicative
import cats.data.{NonEmptyList, ValidatedNel}
import cats.data.Validated._
import cats.instances.list._
import cats.instances.option._
import pl.touk.nussknacker.engine.api.{JobData, MetaData, NodeId}
import pl.touk.nussknacker.engine.api.context.{OutputVar, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.NodeCompilationResult
import pl.touk.nussknacker.engine.compiledgraph
import pl.touk.nussknacker.engine.compiledgraph.node
import pl.touk.nussknacker.engine.compiledgraph.node.{FragmentUsageEnd, Node}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.splittedgraph._
import pl.touk.nussknacker.engine.splittedgraph.splittednode.{Next, SplittedNode}

class PartSubGraphCompiler(nodeCompiler: NodeCompiler) {

  import CompilationResult._

  def validate(n: splittednode.SplittedNode[_], ctx: ValidationContext)(
      implicit jobData: JobData
  ): CompilationResult[Unit] = {
    compile(n, ctx).map(_ => ())
  }

  /* TODO:
  1. Separate validation logic for expressions in nodes and expression not bounded to nodes (e.g. expressions in process properties).
     This way we can make non-optional fieldName
   */
  def compile(n: SplittedNode[_], ctx: ValidationContext)(
      implicit jobData: JobData
  ): CompilationResult[compiledgraph.node.Node] = {
    implicit val nodeId: NodeId = NodeId(n.id)

    def toCompilationResult[T](
        validated: ValidatedNel[ProcessCompilationError, T],
        expressionsTypingInfo: Map[String, ExpressionTypingInfo]
    ) =
      CompilationResult(Map(n.id -> NodeTypingInfo(ctx, expressionsTypingInfo, None)), validated)

    n match {
      case splittednode.SourceNode(nodeData, next)          => handleSourceNode(nodeData, ctx, next)
      case splittednode.OneOutputSubsequentNode(data, next) => compileSubsequent(ctx, data, next)

      case splittednode.SplitNode(bareNode, nexts) =>
        val compiledNexts = nexts.map(n => compile(n, ctx)).sequence
        compiledNexts.andThen(nx =>
          toCompilationResult(Valid(compiledgraph.node.SplitNode(bareNode.id, nx)), Map.empty)
        )

      case splittednode.FilterNode(f @ Filter(id, _, _, _), nextTrue, nextFalse) =>
        val NodeCompilationResult(typingInfo, _, _, compiledExpression, _) =
          nodeCompiler.compileFilter(f, ctx)

        CompilationResult.map3(
          f0 = toCompilationResult(compiledExpression, typingInfo),
          f1 = nextTrue.map(next => compile(next, ctx)).sequence,
          f2 = nextFalse.map(next => compile(next, ctx)).sequence
        )((expr, next, nextFalse) =>
          compiledgraph.node.Filter(
            id = id,
            expression = expr,
            nextTrue = next,
            nextFalse = nextFalse,
            isDisabled = f.isDisabled.contains(true)
          )
        )

      case splittednode.SwitchNode(Switch(id, expression, varName, _), nexts, defaultNext) =>
        val result = nodeCompiler.compileSwitch(
          Applicative[Option].product(varName, expression),
          nexts.map(c => (c.node.id, c.expression)),
          ctx
        )
        val contextAfter = result.validationContext.getOrElse(ctx)

        CompilationResult.map4(
          f0 = CompilationResult(result.validationContext),
          f1 = toCompilationResult(result.compiledObject, result.expressionTypingInfo),
          f2 = nexts.map(caseNode => compile(caseNode.node, contextAfter)).sequence,
          f3 = defaultNext.map(dn => compile(dn, contextAfter)).sequence
        ) { case (_, (expr, caseExpressions), cases, defaultNext) =>
          val compiledCases = caseExpressions.zip(cases).map(k => compiledgraph.node.Case(k._1, k._2))
          compiledgraph.node.Switch(id, Applicative[Option].product(varName, expr), compiledCases, defaultNext)
        }
      case splittednode.EndingNode(data) => compileEndingNode(ctx, data)

    }
  }

  private def handleSourceNode(nodeData: StartingNodeData, ctx: ValidationContext, next: splittednode.Next)(
      implicit jobData: JobData
  ): CompilationResult[node.Source] = {
    // just like in a custom node we can't add input context here because it contains output variable context (not input)
    nodeData match {
      case Source(id, ref, _) =>
        compile(next, ctx).map(nwc => compiledgraph.node.Source(id, Some(ref.typ), nwc))
      case Join(id, _, _, _, _, _) =>
        compile(next, ctx).map(nwc => compiledgraph.node.Source(id, None, nwc))
      case FragmentInputDefinition(id, _, _) =>
        // TODO: should we recognize we're compiling only fragment?
        compile(next, ctx).map(nwc => compiledgraph.node.Source(id, None, nwc))
    }
  }

  private def compileEndingNode(
      ctx: ValidationContext,
      data: EndingNodeData
  )(implicit nodeId: NodeId, jobData: JobData): CompilationResult[compiledgraph.node.Node] = {
    def toCompilationResult[T](
        validated: ValidatedNel[ProcessCompilationError, T],
        expressionsTypingInfo: Map[String, ExpressionTypingInfo],
        parameters: Option[List[Parameter]]
    ) =
      CompilationResult(Map(nodeId.id -> NodeTypingInfo(ctx, expressionsTypingInfo, parameters)), validated)

    data match {
      case processor @ Processor(id, _, disabled, _) =>
        val NodeCompilationResult(typingInfo, parameters, _, validatedServiceRef, _) =
          nodeCompiler.compileProcessor(processor, ctx)
        toCompilationResult(
          validatedServiceRef.map(ref => compiledgraph.node.EndingProcessor(id, ref, disabled.contains(true))),
          typingInfo,
          parameters
        )

      case Sink(id, ref, _, disabled, _) =>
        toCompilationResult(Valid(compiledgraph.node.Sink(id, ref.typ, disabled.contains(true))), Map.empty, None)

      case CustomNode(id, _, nodeType, _, _) =>
        toCompilationResult(Valid(compiledgraph.node.EndingCustomNode(id, nodeType)), Map.empty, None)

      // probably this shouldn't occur - otherwise we'd have empty fragment?
      case FragmentInput(id, _, _, _, _) =>
        toCompilationResult(Invalid(NonEmptyList.of(UnresolvedFragment(id))), Map.empty, None)

      case FragmentOutputDefinition(id, _, List(), _) =>
        // TODO: should we validate it's process?
        // TODO: does it make sense to validate FragmentOutput?
        toCompilationResult(
          Valid(compiledgraph.node.FragmentOutput(id, Map.empty, isDisabled = false)),
          Map.empty,
          None
        )
      case FragmentOutputDefinition(id, _, fields, _) =>
        val fieldTypedExpressions = nodeCompiler.fieldToTypedExpression(fields, ctx)
        toCompilationResult(
          fieldTypedExpressions.map(typedExpressions =>
            compiledgraph.node.FragmentOutput(id, typedExpressions, isDisabled = false)
          ),
          expressionsTypingInfo = Map.empty,
          parameters = None
        )
      // TODO JOIN: a lot of additional validations needed here - e.g. that join with that name exists, that it
      // accepts this join, maybe we should also validate the graph is connected?
      case BranchEndData(definition) =>
        toCompilationResult(Valid(compiledgraph.node.BranchEnd(definition)), Map.empty, None)
    }
  }

  private def compileSubsequent(ctx: ValidationContext, data: OneOutputSubsequentNodeData, next: Next)(
      implicit nodeId: NodeId,
      jobData: JobData
  ): CompilationResult[Node] = {
    def toCompilationResult[T](
        validated: ValidatedNel[ProcessCompilationError, T],
        expressionsTypingInfo: Map[String, ExpressionTypingInfo],
        parameters: Option[List[Parameter]]
    ) =
      CompilationResult(Map(data.id -> NodeTypingInfo(ctx, expressionsTypingInfo, parameters)), validated)

    data match {
      case variable @ Variable(id, varName, _, _) =>
        val NodeCompilationResult(typingInfo, parameters, newCtx, compiledExpression, t) =
          nodeCompiler.compileVariable(variable, ctx)
        CompilationResult.map3(
          f0 = CompilationResult(newCtx),
          f1 = toCompilationResult(compiledExpression, typingInfo, parameters),
          f2 = compile(next, newCtx.getOrElse(ctx))
        ) { (_, compiled, compiledNext) =>
          compiledgraph.node.VariableBuilder(id, varName, Left(compiled), compiledNext)
        }
      case VariableBuilder(id, varName, fields, _) =>
        val NodeCompilationResult(typingInfo, parameters, newCtxV, compiledFields, _) =
          nodeCompiler.compileFields(fields, ctx, outputVar = Some(OutputVar.variable(varName)))
        CompilationResult.map3(
          f0 = CompilationResult(newCtxV),
          f1 = toCompilationResult(compiledFields, typingInfo, parameters),
          f2 = compile(next, newCtxV.getOrElse(ctx))
        ) { (_, compiledFields, compiledNext) =>
          compiledgraph.node.VariableBuilder(id, varName, Right(compiledFields), compiledNext)
        }

      case processor @ Processor(id, _, isDisabled, _) =>
        val NodeCompilationResult(typingInfo, parameters, _, validatedServiceRef, _) =
          nodeCompiler.compileProcessor(processor, ctx)
        CompilationResult.map2(toCompilationResult(validatedServiceRef, typingInfo, parameters), compile(next, ctx))(
          (ref, next) => compiledgraph.node.Processor(id, ref, next, isDisabled.contains(true))
        )

      case enricher @ Enricher(id, _, output, _) =>
        val NodeCompilationResult(typingInfo, parameters, newCtx, validatedServiceRef, _) =
          nodeCompiler.compileEnricher(enricher, ctx, outputVar = OutputVar.enricher(output))

        CompilationResult.map3(
          toCompilationResult(validatedServiceRef, typingInfo, parameters),
          CompilationResult(newCtx),
          compile(next, newCtx.getOrElse(ctx))
        )((ref, _, next) => compiledgraph.node.Enricher(id, ref, output, next))

      // here we don't do anything, in subgraphcompiler it's just pass through, we can't add input context here because it contains output variable context (not input)
      case CustomNode(id, _, nodeType, _, _) =>
        CompilationResult.map(fa = compile(next, ctx))(
          f = compiledNext => compiledgraph.node.CustomNode(id, nodeType, compiledNext)
        )

      case fragmentInput: FragmentInput =>
        val NodeCompilationResult(typingInfo, parameters, newCtx, combinedValidParams, _) =
          nodeCompiler.compileFragmentInput(fragmentInput, ctx)
        CompilationResult.map2(
          toCompilationResult(combinedValidParams, typingInfo, parameters),
          compile(next, newCtx.getOrElse(ctx))
        )((params, next) => compiledgraph.node.FragmentUsageStart(fragmentInput.id, params, next))

      case FragmentUsageOutput(id, outputName, None, _) =>
        // Missing 'parent context' means that fragment has used some component which cleared context. We compile next parts using empty context (but with copied global variables).
        val parentContext = ctx.popContextOrEmptyWithGlobals()
        compile(next, parentContext)
          .andThen(compiledNext =>
            toCompilationResult(Valid(FragmentUsageEnd(id, None, compiledNext)), Map.empty, None)
          )
      case FragmentUsageOutput(id, outputName, Some(outputVar), _) =>
        val NodeCompilationResult(typingInfo, parameters, ctxWithSubOutV, compiledFields, typingResult) =
          nodeCompiler.compileFields(outputVar.fields, ctx, outputVar = None)
        // Missing 'parent context' means that fragment has used some component which cleared context. We compile next parts using empty context (but with copied global variables).
        val parentCtx = ctx.popContextOrEmptyWithGlobals()
        val parentCtxWithSubOut = parentCtx
          .withVariable(OutputVar.fragmentOutput(outputName, outputVar.name), typingResult.getOrElse(Unknown))

        CompilationResult.map4(
          f0 = CompilationResult(ctxWithSubOutV),
          f1 = CompilationResult(parentCtxWithSubOut),
          f2 = toCompilationResult(compiledFields, typingInfo, parameters),
          f3 = compile(next, parentCtxWithSubOut.getOrElse(parentCtx))
        ) { (_, _, compiledFields, compiledNext) =>
          compiledgraph.node.FragmentUsageEnd(
            id,
            Some(node.FragmentOutputVarDefinition(outputVar.name, compiledFields)),
            compiledNext
          )
        }
    }
  }

  private def compile(next: splittednode.Next, ctx: ValidationContext)(
      implicit jobData: JobData
  ): CompilationResult[compiledgraph.node.Next] = {
    next match {
      case splittednode.NextNode(n) => compile(n, ctx).map(cn => compiledgraph.node.NextNode(cn))
      case splittednode.PartRef(ref) =>
        CompilationResult(Map(ref -> NodeTypingInfo(ctx, Map.empty, None)), Valid(compiledgraph.node.PartRef(ref)))
    }
  }

  def withLabelsDictTyper: PartSubGraphCompiler =
    new PartSubGraphCompiler(nodeCompiler.withLabelsDictTyper)

}
