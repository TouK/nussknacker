package pl.touk.nussknacker.engine.compile

import cats.Applicative
import cats.data.Validated._
import cats.data.ValidatedNel
import cats.instances.list._
import cats.instances.option._
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.{OutputVar, ProcessCompilationError}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.NodeCompilationResult
import pl.touk.nussknacker.engine.compile.nodecompilation.{NodeCompiler, SingleInputNodeInputValidationContext}
import pl.touk.nussknacker.engine.compiledgraph.node
import pl.touk.nussknacker.engine.compiledgraph.node.{FragmentUsageEnd, Node}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.splittedgraph._
import pl.touk.nussknacker.engine.splittedgraph.splittednode.{Next, SplittedNode}
import pl.touk.nussknacker.engine.{ScenarioCompilationDependencies, compiledgraph}

class PartSubGraphCompiler(nodeCompiler: NodeCompiler) {

  import CompilationResult._

  def validate(n: splittednode.SplittedNode[_], inputContext: SingleInputNodeInputValidationContext)(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): CompilationResult[Unit] = {
    compile(n, inputContext).map(_ => ())
  }

  /* TODO:
  1. Separate validation logic for expressions in nodes and expression not bounded to nodes (e.g. expressions in process properties).
     This way we can make non-optional fieldName
   */
  def compile(n: SplittedNode[_], inputContext: SingleInputNodeInputValidationContext)(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): CompilationResult[compiledgraph.node.Node] = {
    def toCompilationResult[T](
        validated: ValidatedNel[ProcessCompilationError, T],
        expressionsTypingInfo: Map[String, ExpressionTypingInfo]
    ) =
      CompilationResult(
        Map(n.id -> NodeTypingInfo(inputContext.validationContext, expressionsTypingInfo, None)),
        validated
      )

    n match {
      case splittednode.SourceNode(nodeData, next)          => handleSourceNode(nodeData, inputContext, next)
      case splittednode.OneOutputSubsequentNode(data, next) => compileSubsequent(inputContext, data, next)

      case splittednode.SplitNode(bareNode, nexts) =>
        val compiledNexts = nexts.map(n => compile(n, inputContext)).sequence
        compiledNexts.andThen(nx =>
          toCompilationResult(Valid(compiledgraph.node.SplitNode(bareNode.id, nx.flatten)), Map.empty)
        )

      case splittednode.FilterNode(f @ Filter(id, _, _, _), nextTrue, nextFalse) =>
        val NodeCompilationResult(typingInfo, _, _, compiledExpression, _) =
          nodeCompiler.compileFilter(f, inputContext)

        CompilationResult.map3(
          f0 = toCompilationResult(compiledExpression, typingInfo),
          f1 = nextTrue.map(next => compile(next, inputContext)).sequence,
          f2 = nextFalse.map(next => compile(next, inputContext)).sequence
        )((expr, next, nextFalse) =>
          compiledgraph.node.Filter(
            id = id,
            expression = expr,
            nextTrue = next.flatten,
            nextFalse = nextFalse.flatten,
            isDisabled = f.isDisabled.contains(true)
          )
        )

      case splittednode.SwitchNode(switch @ Switch(id, _, varName, _), nexts, defaultNext) =>
        val result = nodeCompiler.compileSwitch(
          switch,
          nexts.flatMap(c => c.node.map(next => (next.id, c.expression))),
          inputContext
        )
        val contextAfter =
          result.validationContext.map(SingleInputNodeInputValidationContext(_)).getOrElse(inputContext)

        CompilationResult.map4(
          f0 = CompilationResult(result.validationContext),
          f1 = toCompilationResult(result.compiledObject, result.expressionTypingInfo),
          f2 = nexts.map(caseNode => compile(caseNode.node, contextAfter)).sequence,
          f3 = defaultNext.map(dn => compile(dn, contextAfter)).sequence
        ) { case (_, (expr, caseExpressions), cases, defaultNext) =>
          val compiledCases = caseExpressions.zip(cases).map(k => compiledgraph.node.Case(k._1, k._2))
          compiledgraph.node.Switch(id, Applicative[Option].product(varName, expr), compiledCases, defaultNext.flatten)
        }
      case splittednode.EndingNode(data) => compileEndingNode(inputContext, data)

    }
  }

  private def handleSourceNode(
      nodeData: StartingNodeData,
      inputContext: SingleInputNodeInputValidationContext,
      next: Option[splittednode.Next]
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): CompilationResult[node.Source] = {
    // just like in a custom node we can't add input context here because it contains output variable context (not input)
    nodeData match {
      case Source(id, ref, _) =>
        compile(next, inputContext).map(nwc => compiledgraph.node.Source(id, Some(ref.typ), nwc))
      case Join(id, _, _, _, _, _) =>
        compile(next, inputContext).map(nwc => compiledgraph.node.Source(id, None, nwc))
      case FragmentInputDefinition(id, _, _) =>
        // TODO: should we recognize we're compiling only fragment?
        compile(next, inputContext).map(nwc => compiledgraph.node.Source(id, None, nwc))
    }
  }

  private def compileEndingNode(
      inputContext: SingleInputNodeInputValidationContext,
      data: EndingNodeData
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): CompilationResult[compiledgraph.node.Node] = {
    import scenarioCompilationDependencies._
    implicit val nodeId: NodeId = NodeId(data.id)
    def toCompilationResult[T](
        validated: ValidatedNel[ProcessCompilationError, T],
        expressionsTypingInfo: Map[String, ExpressionTypingInfo],
        parameters: Option[List[Parameter]]
    ) =
      CompilationResult(
        Map(nodeId.id -> NodeTypingInfo(inputContext.validationContext, expressionsTypingInfo, parameters)),
        validated
      )

    data match {
      case processor @ Processor(id, _, disabled, _) =>
        val NodeCompilationResult(typingInfo, parameters, _, validatedServiceRef, _) =
          nodeCompiler.compileProcessor(processor, inputContext)
        toCompilationResult(
          validatedServiceRef.map(ref => compiledgraph.node.EndingProcessor(id, ref, disabled.contains(true))),
          typingInfo,
          parameters
        )

      case Sink(id, ref, _, disabled, _) =>
        toCompilationResult(Valid(compiledgraph.node.Sink(id, ref.typ, disabled.contains(true))), Map.empty, None)

      case CustomNode(id, _, nodeType, _, _) =>
        toCompilationResult(Valid(compiledgraph.node.EndingCustomNode(id, nodeType)), Map.empty, None)

      // this occurs, when we have fragment without a sink
      case fragmentInput: FragmentInput =>
        val NodeCompilationResult(typingInfo, parameters, _, combinedValidParams, _) =
          nodeCompiler.compileFragmentInput(fragmentInput, inputContext)
        toCompilationResult(combinedValidParams, typingInfo, parameters).map { params =>
          compiledgraph.node.FragmentUsageStart(fragmentInput.id, params, None)
        }
      case FragmentOutputDefinition(id, _, Nil, _) =>
        // TODO: should we validate it's process?
        // TODO: does it make sense to validate FragmentOutput?
        toCompilationResult(
          Valid(compiledgraph.node.FragmentOutput(id, Map.empty, isDisabled = false)),
          Map.empty,
          None
        )
      case fod @ FragmentOutputDefinition(id, _, _, _) =>
        val fieldTypedExpressions = nodeCompiler.compileFragmentOutputDefinition(fod, inputContext)
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

  private def compileSubsequent(
      inputContext: SingleInputNodeInputValidationContext,
      data: OneOutputSubsequentNodeData,
      next: Option[Next]
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): CompilationResult[Node] = {
    import scenarioCompilationDependencies._

    def toCompilationResult[T](
        validated: ValidatedNel[ProcessCompilationError, T],
        expressionsTypingInfo: Map[String, ExpressionTypingInfo],
        parameters: Option[List[Parameter]]
    ) =
      CompilationResult(
        Map(data.id -> NodeTypingInfo(inputContext.validationContext, expressionsTypingInfo, parameters)),
        validated
      )

    data match {
      case variable @ Variable(id, varName, _, _) =>
        val NodeCompilationResult(typingInfo, parameters, newCtx, compiledExpression, t) =
          nodeCompiler.compileVariable(variable, inputContext)
        CompilationResult.map3(
          f0 = CompilationResult(newCtx),
          f1 = toCompilationResult(compiledExpression, typingInfo, parameters),
          f2 = compile(next, newCtx.map(SingleInputNodeInputValidationContext(_)).getOrElse(inputContext))
        ) { (_, compiled, compiledNext) =>
          compiledgraph.node.VariableBuilder(id, varName, Left(compiled), compiledNext)
        }
      case VariableBuilder(id, varName, fields, _) =>
        implicit val nodeId: NodeId = NodeId(id)
        val NodeCompilationResult(typingInfo, parameters, newCtxV, compiledFields, _) =
          nodeCompiler.compileFields(fields, inputContext, outputVar = Some(OutputVar.variable(varName)))
        CompilationResult.map3(
          f0 = CompilationResult(newCtxV),
          f1 = toCompilationResult(compiledFields, typingInfo, parameters),
          f2 = compile(next, newCtxV.map(SingleInputNodeInputValidationContext(_)).getOrElse(inputContext))
        ) { (_, compiledFields, compiledNext) =>
          compiledgraph.node.VariableBuilder(id, varName, Right(compiledFields), compiledNext)
        }

      case processor @ Processor(id, _, isDisabled, _) =>
        val NodeCompilationResult(typingInfo, parameters, _, validatedServiceRef, _) =
          nodeCompiler.compileProcessor(processor, inputContext)
        CompilationResult.map2(
          toCompilationResult(validatedServiceRef, typingInfo, parameters),
          compile(next, inputContext)
        )((ref, next) => compiledgraph.node.Processor(id, ref, next, isDisabled.contains(true)))

      case enricher @ Enricher(id, _, output, _, _) =>
        val NodeCompilationResult(typingInfo, parameters, newCtx, validatedServiceRef, _) =
          nodeCompiler.compileEnricher(enricher, inputContext)

        CompilationResult.map3(
          toCompilationResult(validatedServiceRef, typingInfo, parameters),
          CompilationResult(newCtx),
          compile(next, newCtx.map(SingleInputNodeInputValidationContext(_)).getOrElse(inputContext))
        )((serviceCompilationResult, _, next) =>
          compiledgraph.node.Enricher(
            id,
            serviceCompilationResult.serviceRef,
            output,
            next,
            serviceCompilationResult.mockOutputExpression
          )
        )

      // here we don't do anything, in subgraphcompiler it's just pass through, we can't add input context here because it contains output variable context (not input)
      case CustomNode(id, _, nodeType, _, _) =>
        CompilationResult.map(fa = compile(next, inputContext))(
          f = compiledNext => compiledgraph.node.CustomNode(id, nodeType, compiledNext)
        )

      case fragmentInput: FragmentInput =>
        val NodeCompilationResult(typingInfo, parameters, newCtx, combinedValidParams, _) =
          nodeCompiler.compileFragmentInput(fragmentInput, inputContext)
        CompilationResult.map2(
          toCompilationResult(combinedValidParams, typingInfo, parameters),
          compile(next, newCtx.map(SingleInputNodeInputValidationContext(_)).getOrElse(inputContext))
        )((params, next) => compiledgraph.node.FragmentUsageStart(fragmentInput.id, params, next))

      case FragmentUsageOutput(id, fragmentUsageStartNodeId, outputName, None, _) =>
        // Missing 'parent context' means that fragment has used some component which cleared context. We compile next parts using empty context (but with copied global variables).
        val parentContext = inputContext.validationContext.popContextOrEmptyWithGlobals()
        compile(next, SingleInputNodeInputValidationContext(parentContext))
          .andThen(compiledNext =>
            toCompilationResult(
              Valid(FragmentUsageEnd(id, fragmentUsageStartNodeId, None, compiledNext)),
              Map.empty,
              None
            )
          )
      case FragmentUsageOutput(id, fragmentUsageStartNodeId, outputName, Some(outputVar), _) =>
        implicit val nodeId: NodeId = NodeId(id)
        val NodeCompilationResult(typingInfo, parameters, ctxWithSubOutV, compiledFields, typingResult) =
          nodeCompiler.compileFields(outputVar.fields, inputContext, outputVar = None)
        // Missing 'parent context' means that fragment has used some component which cleared context. We compile next parts using empty context (but with copied global variables).
        val parentCtx = inputContext.validationContext.popContextOrEmptyWithGlobals()
        val parentCtxWithSubOut = parentCtx
          .withVariable(OutputVar.fragmentOutput(outputName, outputVar.name), typingResult.getOrElse(Unknown))

        CompilationResult.map4(
          f0 = CompilationResult(ctxWithSubOutV),
          f1 = CompilationResult(parentCtxWithSubOut),
          f2 = toCompilationResult(compiledFields, typingInfo, parameters),
          f3 = compile(next, SingleInputNodeInputValidationContext(parentCtxWithSubOut.getOrElse(parentCtx)))
        ) { (_, _, compiledFields, compiledNext) =>
          compiledgraph.node.FragmentUsageEnd(
            id,
            fragmentUsageStartNodeId,
            Some(node.FragmentOutputVarDefinition(outputVar.name, compiledFields)),
            compiledNext
          )
        }
    }
  }

  private def compile(nextOpt: Option[splittednode.Next], inputContext: SingleInputNodeInputValidationContext)(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): CompilationResult[Option[compiledgraph.node.Next]] = {
    nextOpt match {
      case Some(next) => compile(next, inputContext)
      case None       => CompilationResult(Map.empty, Valid(None))
    }
  }

  private def compile(next: splittednode.Next, inputContext: SingleInputNodeInputValidationContext)(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): CompilationResult[Option[compiledgraph.node.Next]] = {
    next match {
      case splittednode.NextNode(n) =>
        compile(n, inputContext)
          .map(cn => compiledgraph.node.NextNode(cn))
          .map(Some(_))
      case splittednode.PartRef(ref) =>
        CompilationResult(
          Map(ref -> NodeTypingInfo(inputContext.validationContext, Map.empty, None)),
          Valid(compiledgraph.node.PartRef(ref))
        )
          .map(Some(_))
    }
  }

  def withLabelsDictTyper: PartSubGraphCompiler =
    new PartSubGraphCompiler(nodeCompiler.withLabelsDictTyper)

}
