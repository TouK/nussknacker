package pl.touk.nussknacker.engine.compile

import cats.Applicative
import cats.data.Validated._
import cats.data.ValidatedNel
import cats.instances.list._
import cats.instances.option._
import pl.touk.nussknacker.engine.{compiledgraph, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.{OutputVar, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.compile.nodecompilation.{NodeCompiler, SingleInputNodeInputValidationContext}
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.NodeCompilationResult
import pl.touk.nussknacker.engine.compiledgraph.node
import pl.touk.nussknacker.engine.compiledgraph.node.{FragmentUsageEnd, Node}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.splittedgraph._
import pl.touk.nussknacker.engine.splittedgraph.splittednode.{Next, SplittedNode}

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
        Map(
          n.id.value -> NodeTypingInfo(
            inputContext.validationContext,
            expressionsTypingInfo,
            parameters = None,
            outputValidationContext = None
          )
        ),
        validated
      )

    n match {
      case splittednode.SourceNode(nodeData, next)          => handleSourceNode(nodeData, inputContext, next)
      case splittednode.OneOutputSubsequentNode(data, next) => compileSubsequent(inputContext, data, next)

      case splittednode.SplitNode(bareNode, nexts) =>
        val compiledNexts = nexts.map(n => compile(n, inputContext)).sequence
        compiledNexts.andThen(nx =>
          toCompilationResult(
            Valid(compiledgraph.node.SplitNode(bareNode.id.value, bareNode.name.value, nx.flatten)),
            Map.empty
          )
        )

      case splittednode.FilterNode(f @ Filter(id, _, _, _, _), nextTrue, nextFalse) =>
        val NodeCompilationResult(typingInfo, _, _, compiledExpression, _) =
          nodeCompiler.compileFilter(f, inputContext)

        CompilationResult.map3(
          f0 = toCompilationResult(compiledExpression, typingInfo),
          f1 = nextTrue.map(next => compile(next, inputContext)).sequence,
          f2 = nextFalse.map(next => compile(next, inputContext)).sequence
        )((expr, next, nextFalse) =>
          compiledgraph.node.Filter(
            id = id.value,
            name = f.name.value,
            expression = expr,
            nextTrue = next.flatten,
            nextFalse = nextFalse.flatten,
            isDisabled = f.isDisabled.contains(true)
          )
        )

      case splittednode.SwitchNode(switch @ Switch(id, _, _, varName, _), nexts, defaultNext) =>
        val result = nodeCompiler.compileSwitch(
          switch,
          nexts.flatMap(c => c.node.map(next => (next.id.value, c.expression))),
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
          compiledgraph.node.Switch(
            id.value,
            switch.name.value,
            Applicative[Option].product(varName, expr),
            compiledCases,
            defaultNext.flatten
          )
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
      case Source(id, name, ref, _) =>
        compile(next, inputContext).map(nwc => compiledgraph.node.Source(id.value, name.value, Some(ref.typ), nwc))
      case Join(id, name, _, _, _, _, _) =>
        compile(next, inputContext).map(nwc => compiledgraph.node.Source(id.value, name.value, None, nwc))
      case FragmentInputDefinition(id, name, _, _) =>
        // TODO: should we recognize we're compiling only fragment?
        compile(next, inputContext).map(nwc => compiledgraph.node.Source(id.value, name.value, None, nwc))
    }
  }

  private def compileEndingNode(
      inputContext: SingleInputNodeInputValidationContext,
      data: EndingNodeData
  )(
      implicit scenarioCompilationDependencies: ScenarioCompilationDependencies
  ): CompilationResult[compiledgraph.node.Node] = {
    import scenarioCompilationDependencies._
    implicit val nodeId: NodeId = data.id
    def toCompilationResult[T](
        validated: ValidatedNel[ProcessCompilationError, T],
        expressionsTypingInfo: Map[String, ExpressionTypingInfo],
        parameters: Option[List[Parameter]],
        outputValidationContext: Option[ValidationContext]
    ) =
      CompilationResult(
        Map(
          nodeId.value -> NodeTypingInfo(
            inputContext.validationContext,
            expressionsTypingInfo,
            parameters,
            outputValidationContext
          )
        ),
        validated
      )

    data match {
      case processor @ Processor(id, _, _, disabled, _) =>
        val NodeCompilationResult(typingInfo, parameters, _, validatedServiceRef, _) =
          nodeCompiler.compileProcessor(processor, inputContext)
        toCompilationResult(
          validatedServiceRef.map(ref =>
            compiledgraph.node.EndingProcessor(id.value, processor.name.value, ref, disabled.contains(true))
          ),
          typingInfo,
          parameters,
          outputValidationContext = Some(inputContext.validationContext)
        )

      case Sink(id, name, ref, _, disabled, _) =>
        toCompilationResult(
          Valid(compiledgraph.node.Sink(id.value, name.value, ref.typ, disabled.contains(true))),
          expressionsTypingInfo = Map.empty,
          parameters = None,
          outputValidationContext = None
        )

      case CustomNode(id, name, _, nodeType, _, _) =>
        toCompilationResult(
          Valid(compiledgraph.node.EndingCustomNode(id.value, name.value, nodeType)),
          expressionsTypingInfo = Map.empty,
          parameters = None,
          outputValidationContext = None
        )

      // this occurs, when we have fragment without a sink
      case fragmentInput: FragmentInput =>
        val NodeCompilationResult(typingInfo, parameters, _, combinedValidParams, _) =
          nodeCompiler.compileFragmentInput(fragmentInput, inputContext)
        toCompilationResult(combinedValidParams, typingInfo, parameters, Some(inputContext.validationContext)).map {
          params =>
            compiledgraph.node.FragmentUsageStart(fragmentInput.id.value, fragmentInput.name.value, params, None)
        }
      case FragmentOutputDefinition(id, name, _, Nil, _) =>
        // TODO: should we validate it's process?
        // TODO: does it make sense to validate FragmentOutput?
        toCompilationResult(
          Valid(compiledgraph.node.FragmentOutput(id.value, name.value, Map.empty, isDisabled = false)),
          expressionsTypingInfo = Map.empty,
          parameters = None,
          outputValidationContext = None
        )
      case fod @ FragmentOutputDefinition(id, _, _, _, _) =>
        val fieldTypedExpressions = nodeCompiler.compileFragmentOutputDefinition(fod, inputContext)
        toCompilationResult(
          fieldTypedExpressions.map(typedExpressions =>
            compiledgraph.node.FragmentOutput(id.value, fod.name.value, typedExpressions, isDisabled = false)
          ),
          expressionsTypingInfo = Map.empty,
          parameters = None,
          outputValidationContext = None
        )
      // TODO JOIN: a lot of additional validations needed here - e.g. that join with that name exists, that it
      // accepts this join, maybe we should also validate the graph is connected?
      case BranchEndData(definition) =>
        toCompilationResult(
          Valid(compiledgraph.node.BranchEnd(definition)),
          expressionsTypingInfo = Map.empty,
          parameters = None,
          outputValidationContext = None
        )
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
        parameters: Option[List[Parameter]],
        outputValidationContext: Option[ValidationContext],
    ) =
      CompilationResult(
        Map(
          data.id.value -> NodeTypingInfo(
            inputContext.validationContext,
            expressionsTypingInfo,
            parameters,
            outputValidationContext
          )
        ),
        validated
      )

    data match {
      case variable @ Variable(id, _, varName, _, _, operation, _) =>
        operation match {
          case VariableOperation.Set =>
            val NodeCompilationResult(typingInfo, parameters, newCtx, compiledExpression, _) =
              nodeCompiler.compileVariable(variable, inputContext)
            CompilationResult.map3(
              f0 = CompilationResult(newCtx),
              f1 = toCompilationResult(compiledExpression, typingInfo, parameters, newCtx.toOption),
              f2 = compile(next, newCtx.map(SingleInputNodeInputValidationContext(_)).getOrElse(inputContext))
            ) { (_, compiled, compiledNext) =>
              compiledgraph.node.VariableBuilder(id.value, variable.name.value, varName, Left(compiled), compiledNext)
            }
          case VariableOperation.Unset =>
            val NodeCompilationResult(typingInfo, parameters, newCtx, variablesToUnset, _) =
              nodeCompiler.compileUnsetVariable(variable, inputContext)
            CompilationResult.map3(
              f0 = CompilationResult(newCtx),
              f1 = toCompilationResult(variablesToUnset, typingInfo, parameters, newCtx.toOption),
              f2 = compile(next, newCtx.map(SingleInputNodeInputValidationContext(_)).getOrElse(inputContext))
            ) { (_, compiledUnsetVariables, compiledNext) =>
              compiledgraph.node.VariableBuilder(
                id = id.value,
                name = variable.name.value,
                varName = varName,
                value = Right(Nil),
                next = compiledNext,
                unsetVariables = compiledUnsetVariables
              )
            }
        }
      case vb @ VariableBuilder(id, _, varName, fields, _) =>
        implicit val nodeId: NodeId = id
        val NodeCompilationResult(typingInfo, parameters, newCtxV, compiledFields, _) =
          nodeCompiler.compileFields(fields, inputContext, outputVar = Some(OutputVar.variable(varName)))
        CompilationResult.map3(
          f0 = CompilationResult(newCtxV),
          f1 = toCompilationResult(compiledFields, typingInfo, parameters, newCtxV.toOption),
          f2 = compile(next, newCtxV.map(SingleInputNodeInputValidationContext(_)).getOrElse(inputContext))
        ) { (_, compiledFields, compiledNext) =>
          compiledgraph.node.VariableBuilder(id.value, vb.name.value, varName, Right(compiledFields), compiledNext)
        }

      case processor @ Processor(id, _, _, isDisabled, _) =>
        val NodeCompilationResult(typingInfo, parameters, _, validatedServiceRef, _) =
          nodeCompiler.compileProcessor(processor, inputContext)
        CompilationResult.map2(
          toCompilationResult(validatedServiceRef, typingInfo, parameters, Some(inputContext.validationContext)),
          compile(next, inputContext)
        )((ref, next) =>
          compiledgraph.node.Processor(id.value, processor.name.value, ref, next, isDisabled.contains(true))
        )

      case enricher @ Enricher(id, _, _, output, _, _) =>
        val NodeCompilationResult(typingInfo, parameters, newCtx, validatedServiceRef, _) =
          nodeCompiler.compileEnricher(enricher, inputContext)

        CompilationResult.map3(
          toCompilationResult(validatedServiceRef, typingInfo, parameters, newCtx.toOption),
          CompilationResult(newCtx),
          compile(next, newCtx.map(SingleInputNodeInputValidationContext(_)).getOrElse(inputContext))
        )((serviceCompilationResult, _, next) =>
          compiledgraph.node.Enricher(
            id.value,
            enricher.name.value,
            serviceCompilationResult.serviceRef,
            output,
            next,
            serviceCompilationResult.mockOutputExpression
          )
        )

      // here we don't do anything, in subgraphcompiler it's just pass through, we can't add input context here because it contains output variable context (not input)
      case node @ CustomNode(id, _, _, nodeType, _, _) =>
        CompilationResult.map(fa = compile(next, inputContext))(
          f = compiledNext => compiledgraph.node.CustomNode(id.value, node.name.value, nodeType, compiledNext)
        )

      case fragmentInput: FragmentInput =>
        val NodeCompilationResult(typingInfo, parameters, newCtx, combinedValidParams, _) =
          nodeCompiler.compileFragmentInput(fragmentInput, inputContext)
        CompilationResult.map2(
          toCompilationResult(combinedValidParams, typingInfo, parameters, newCtx.toOption),
          compile(next, newCtx.map(SingleInputNodeInputValidationContext(_)).getOrElse(inputContext))
        )((params, next) =>
          compiledgraph.node.FragmentUsageStart(fragmentInput.id.value, fragmentInput.name.value, params, next)
        )

      case f @ FragmentUsageOutput(id, _, fragmentUsageStartNodeId, outputName, None, _) =>
        // Missing 'parent context' means that fragment has used some component which cleared context. We compile next parts using empty context (but with copied global variables).
        val parentContext = inputContext.validationContext.popContextOrEmptyWithGlobals()
        compile(next, SingleInputNodeInputValidationContext(parentContext))
          .andThen(compiledNext =>
            toCompilationResult(
              Valid(FragmentUsageEnd(id.value, f.name.value, fragmentUsageStartNodeId.value, None, compiledNext)),
              expressionsTypingInfo = Map.empty,
              parameters = None,
              outputValidationContext = Some(parentContext)
            )
          )
      case f @ FragmentUsageOutput(id, _, fragmentUsageStartNodeId, outputName, Some(outputVar), _) =>
        implicit val nodeId: NodeId = id
        val NodeCompilationResult(typingInfo, parameters, ctxWithSubOutV, compiledFields, typingResult) =
          nodeCompiler.compileFields(outputVar.fields, inputContext, outputVar = None)
        // Missing 'parent context' means that fragment has used some component which cleared context. We compile next parts using empty context (but with copied global variables).
        val parentCtx = inputContext.validationContext.popContextOrEmptyWithGlobals()
        val parentCtxWithSubOut = parentCtx
          .withVariable(OutputVar.fragmentOutput(outputName, outputVar.name), typingResult.getOrElse(Unknown))

        CompilationResult.map4(
          f0 = CompilationResult(ctxWithSubOutV),
          f1 = CompilationResult(parentCtxWithSubOut),
          f2 = toCompilationResult(compiledFields, typingInfo, parameters, parentCtxWithSubOut.toOption),
          f3 = compile(next, SingleInputNodeInputValidationContext(parentCtxWithSubOut.getOrElse(parentCtx)))
        ) { (_, _, compiledFields, compiledNext) =>
          compiledgraph.node.FragmentUsageEnd(
            id.value,
            f.name.value,
            fragmentUsageStartNodeId.value,
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
          Map(
            ref.value -> NodeTypingInfo(
              inputContext.validationContext,
              expressionsTypingInfo = Map.empty,
              parameters = None,
              outputValidationContext = None
            )
          ),
          Valid(compiledgraph.node.PartRef(ref.value))
        )
          .map(Some(_))
    }
  }

  def withLabelsDictTyper: PartSubGraphCompiler =
    new PartSubGraphCompiler(nodeCompiler.withLabelsDictTyper)

}
