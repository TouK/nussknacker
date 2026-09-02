package pl.touk.nussknacker.engine.lite

import cats.{Monad, Monoid}
import cats.data._
import cats.data.Validated.{Invalid, Valid}
import cats.implicits._
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{ComponentType, NodeComponentInfo, NodesDeploymentData}
import pl.touk.nussknacker.engine.api.context.{JoinContextTransformation, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.UnsupportedPart
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process.{ServiceExecutionContext, Source}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.compile.nodecompilation.SingleInputNodeInputValidationContext
import pl.touk.nussknacker.engine.compiledgraph.CompiledProcessParts
import pl.touk.nussknacker.engine.compiledgraph.node.Node
import pl.touk.nussknacker.engine.compiledgraph.part._
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition
import pl.touk.nussknacker.engine.lite.api.commonTypes.{monoid, DataBatch, ErrorType, ResultType}
import pl.touk.nussknacker.engine.lite.api.customComponentTypes._
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{
  EndResult,
  ScenarioInputBatch,
  ScenarioInterpreter,
  SourceId
}
import pl.touk.nussknacker.engine.resultcollector.{ProductionServiceInvocationCollector, ResultCollector}
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.nussknacker.engine.util.Implicits.{RichIterable, RichScalaMap}
import pl.touk.nussknacker.engine.util.LoggingListener
import pl.touk.nussknacker.engine.util.metrics.common.{
  EndCountingListener,
  ExceptionCountingListener,
  NodeCountingListener
}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

object ScenarioInterpreterFactory {

  type ScenarioInterpreterWithLifecycle[F[_], Input, Res <: AnyRef] = ScenarioInterpreter[F, Input, Res] with Lifecycle

  // types of data produced in sinks
  private type WithSinkTypes[K] = Writer[Map[NodeId, TypingResult], K]

  private type InterpreterOutputType[F[_]] = F[ResultType[PartResult]]

  private type ScenarioInterpreterType[F[_], Input] = ScenarioInputBatch[Input] => InterpreterOutputType[F]

  def createInterpreter[F[_]: Monad, Input, Res <: AnyRef](
      process: CanonicalProcess,
      jobData: JobData,
      nodesDeploymentData: NodesDeploymentData,
      modelData: ModelData,
      additionalListeners: List[ProcessListener] = Nil,
      resultCollector: ResultCollector = ProductionServiceInvocationCollector,
      runtimeMode: RuntimeMode = RuntimeMode.Live,
  )(
      implicit ec: ExecutionContext,
      shape: InterpreterShape[F],
      capabilityTransformer: CapabilityTransformer[F]
  ): ValidatedNel[ProcessCompilationError, ScenarioInterpreterWithLifecycle[F, Input, Res]] =
    modelData.withModelClassloaderAsContextClassLoader {

      val creator = modelData.configCreator

      val allNodes = process.collectAllNodes
      val countingListeners = List(
        LoggingListener,
        new NodeCountingListener(allNodes.map(n => n.id -> n.name).toMap),
        new ExceptionCountingListener,
        new EndCountingListener(allNodes),
      )
      val listeners = creator.listeners(modelData.modelConfig) ++ additionalListeners ++ countingListeners

      val compilerData = ProcessCompilerData.prepare(
        jobData,
        modelData.modelDefinitionWithClasses,
        modelData.engineDictRegistry,
        listeners,
        modelData.modelClassLoader,
        resultCollector,
        runtimeMode,
        modelData.customProcessValidator,
        nodesDeploymentData,
      )

      implicit val engineScenarioCompilationDependencies: EngineScenarioCompilationDependencies =
        EngineScenarioCompilationDependencies.empty
      compilerData.compile(process).andThen { compiledProcess =>
        val startParts = extractStartParts(compiledProcess.sources.toList)
        val sources    = collectSources(startParts)

        val lifecycle = compilerData.lifecycle(allNodes) ++ startParts.values.collect { case lifecycle: Lifecycle =>
          lifecycle
        }
        InvokerCompiler[F, Input, Res](
          compiledProcess,
          compilerData,
          runtimeMode,
          capabilityTransformer
        ).compile
          .map(_.run)
          .map { case (sinkTypes, invoker) =>
            ScenarioInterpreterImpl(sources, sinkTypes, invoker, lifecycle, modelData)
          }
      }
    }

  private def extractStartParts(parts: List[ProcessPart]): Map[String, Any] = {
    parts.foldLeft(Map.empty[String, Any]) { (acc, part) =>
      part match {
        case source: SourcePart =>
          acc + (source.id.value -> source.obj) ++ extractStartParts(source.nextParts)
        case custom: CustomNodePart =>
          acc + (custom.id.value -> custom.transformer) ++ extractStartParts(custom.nextParts)
        case sink: SinkPart =>
          acc + (sink.id.value -> sink.obj)
      }
    }
  }

  private def collectSources(componentById: Map[String, Any]): Map[SourceId, Source] = componentById.collect {
    case (id, a: Source) =>
      (SourceId(id), a)
  }

  private case class ScenarioInterpreterImpl[F[_], Input, Res <: AnyRef](
      sources: Map[SourceId, Source],
      sinkTypes: Map[NodeId, TypingResult],
      private val invoker: ScenarioInterpreterType[F, Input],
      private val lifecycle: Seq[Lifecycle],
      private val modelData: ModelData
  )(implicit monad: Monad[F])
      extends ScenarioInterpreter[F, Input, Res]
      with Lifecycle {

    def invoke(contexts: ScenarioInputBatch[Input]): F[ResultType[EndResult[Res]]] =
      modelData.withModelClassloaderAsContextClassLoader {
        invoker(contexts).map { result =>
          result.map(_.map {
            case e: EndPartResult[Res @unchecked] => EndResult(NodeId(e.nodeId), e.context, e.result)
            case other => throw new IllegalArgumentException(s"Should not happen, $other left in results")
          })
        }
      }

    override def open(context: EngineRuntimeContext): Unit = modelData.withModelClassloaderAsContextClassLoader {
      lifecycle.foreach(_.open(context))
    }

    override def close(): Unit = modelData.withModelClassloaderAsContextClassLoader {
      lifecycle.foreach(_.close())
    }

  }

  private case class InvokerCompiler[F[_]: Monad, Input, Res <: AnyRef](
      compiledProcess: CompiledProcessParts,
      processCompilerData: ProcessCompilerData,
      runtimeMode: RuntimeMode,
      capabilityTransformer: CapabilityTransformer[F],
  )(implicit ec: ExecutionContext, shape: InterpreterShape[F]) {

    private case class PartsComputedToPassAndRun(
        resultPointingToThis: List[JoinResult],
        resultPointingToOthers: List[PartResult],
        endResults: List[PartResult]
    )

    private case class JoinCompilationResult[K](nodeId: NodeId, transformationResult: CompilationResult[K])

    // we collect errors and also typing results of sinks
    private type CompilationResult[K] = ValidatedNel[ProcessCompilationError, WithSinkTypes[K]]

    private type InterpreterOutputType = F[ResultType[PartResult]]

    private type ScenarioInterpreterType = ScenarioInputBatch[Input] => InterpreterOutputType

    private type PartInterpreterType = DataBatch => InterpreterOutputType

    def compile: CompilationResult[ScenarioInterpreterType] = {
      val emptyPartInvocation: ScenarioInterpreterType = (inputs: ScenarioInputBatch[Input]) =>
        Monoid.combineAll(inputs.value.map { case (source, _) =>
          Monad[F].pure[ResultType[PartResult]](
            Writer(
              NuExceptionInfo(
                Some(NodeComponentInfo(NodeId(source.value), NodeName("unknown"), ComponentType.Source, "source")),
                new IllegalArgumentException(s"Unknown source ${source.value}"),
                Context.dummy,
              ) :: Nil,
              Nil
            )
          )
        })

      def computeNextSourceInvocation(
          base: ScenarioInterpreterType,
          nextSource: SourcePart,
          next: Input => F[ResultType[PartResult]]
      ): ScenarioInputBatch[Input] => InterpreterOutputType = (inputs: ScenarioInputBatch[Input]) =>
        Monoid.combineAll(inputs.value.map {
          case (source, ctx) if source.value == nextSource.id.value => next(ctx)
          case other                                                => base(ScenarioInputBatch(other :: Nil))
        })

      // here we rely on the fact that parts are sorted correctly (see ProcessCompiler.compileSources)
      compiledProcess.sources
        .foldLeft[CompilationResult[ScenarioInterpreterType]](
          Valid(Writer(Map.empty[NodeId, TypingResult], emptyPartInvocation))
        ) {
          case (resultSoFar, join: CustomNodePart) =>
            val joinCompilationResult = compileJoinTransformer(join)
            resultSoFar.product(joinCompilationResult.transformationResult).map {
              case (WriterT((types, interpreterMap)), WriterT((types2, part))) =>
                val result = computeNextJoinInvocation(interpreterMap, joinCompilationResult.nodeId, part)
                Writer(types ++ types2, result)
            }
          case (resultSoFar, a: SourcePart) =>
            resultSoFar.product(compiledPartInvoker(a)).andThen {
              case (WriterT((types, interpreter)), WriterT((types2, part))) =>
                compileSource(a).map { compiledSource =>
                  Writer(
                    types ++ types2,
                    computeNextSourceInvocation(
                      interpreter,
                      a,
                      compiledSource andThen { validatedCtx =>
                        validatedCtx.fold(
                          errs => Monad[F].pure(Writer(errs.toList, List.empty)),
                          ctx => part(DataBatch(List(ctx)))
                        )
                      }
                    )
                  )
                }
            }
        }
        .map(_.map(invokeListenersOnException))
    }

    // TODO: Figure out how to invoke this properly. Solution below works when F.map is invoked
    // only once per computation. We cannot guarantee it...
    private def invokeListenersOnException(scenarioInterpreter: ScenarioInterpreterType): ScenarioInterpreterType = {
      scenarioInterpreter.andThen(_.map { case results @ WriterT((exceptions, _)) =>
        exceptions.foreach(ex => processCompilerData.listeners.foreach(_.exceptionThrown(ex)))
        results
      })
    }

    private def compileWithCompilationErrors(
        node: SplittedNode[_],
        validationContext: ValidationContext,
    ): ValidatedNel[ProcessCompilationError, Node] =
      processCompilerData.subPartCompiler
        .compile(node, SingleInputNodeInputValidationContext(validationContext))(
          new ScenarioCompilationDependencies(processCompilerData.jobData, EngineScenarioCompilationDependencies.empty)
        )
        .result

    private def customComponentContext(nodeId: NodeId, nodeName: NodeName) =
      CustomComponentContext[F](nodeId, nodeName, capabilityTransformer)

    // The compiler already rejects a connected additional output here via the `SupportsMultipleOutputs`
    // marker, but the marker is a public trait any component can carry, so don't trust it transitively:
    // wiring only the main output would silently starve every additional output's branch.
    private def validateTransformer[T](nodeId: NodeId, nodeName: NodeName, outputs: NonEmptyList[CompiledOutput])(
        accept: PartialFunction[Any, T]
    )(transformerObj: Any): ValidatedNel[ProcessCompilationError, T] = {
      def unsupported = Invalid(NonEmptyList.of(UnsupportedPart(nodeId, nodeName)))
      if (outputs.tail.nonEmpty) unsupported
      else accept.lift(transformerObj).map(Valid(_)).getOrElse(unsupported)
    }

    private def compiledPartInvoker(
        processPart: ProcessPart,
    ): CompilationResult[PartInterpreterType] =
      processPart match {
        case SourcePart(_, node, validationContext, nextParts, _) =>
          compileWithCompilationErrors(node, validationContext).andThen(partInvoker(_, nextParts))
        case SinkPart(sink, endNode, _, validationContext) =>
          compileWithCompilationErrors(endNode, validationContext).andThen { compiled =>
            partInvoker(compiled, List()).map(prepareResponse(compiled, sink))
          }
        case CustomNodePart(transformerObj, _, validationContext, outputs) =>
          val mainOutput = outputs.head
          val node       = mainOutput.node
          val nodeId     = node.id
          val validatedTransformer =
            validateTransformer(nodeId, node.data.name, outputs) { case t: LiteCustomComponent => t }(transformerObj)
          validatedTransformer.andThen { transformer =>
            val result =
              compileWithCompilationErrors(node, validationContext).andThen(partInvoker(_, mainOutput.nextParts))
            result.map(rs =>
              rs.map(transformer.createTransformation(_, customComponentContext(nodeId, node.data.name)))
            )
          }
      }

    private def prepareResponse(compiledNode: Node, sink: process.Sink)(
        it: WithSinkTypes[PartInterpreterType]
    ): WithSinkTypes[PartInterpreterType] = sink match {
      case sinkWithParams: LiteSink[Res @unchecked] =>
        val (returnType, evaluation) =
          sinkWithParams.createTransformation[F](
            customComponentContext(NodeId(compiledNode.id), NodeName(compiledNode.name))
          )

        it.bimap(
          _.updated(NodeId(compiledNode.id), returnType),
          (originalSink: PartInterpreterType) =>
            (ctxs: DataBatch) => {
              // we invoke 'original sink part' because otherwise listeners wouldn't work properly
              originalSink.apply(ctxs).flatMap { _ =>
                evaluation(ctxs).map(_.map(_.map { case (ctx, result) =>
                  EndPartResult(compiledNode.id, ctx, result)
                }))
              }
            }
        )

      case other =>
        throw new IllegalArgumentException(s"Not supported sink: $other")
    }

    private def compilePartInvokers(parts: List[SubsequentPart]): CompilationResult[Map[String, PartInterpreterType]] =
      parts.map(part => compiledPartInvoker(part).map(compiled => part.id.value -> compiled)).sequence.map { res =>
        Writer(res.flatMap(_._2.written).toMap, res.toMap.mapValuesNow(_.value))
      }

    private def partInvoker(
        node: compiledgraph.node.Node,
        parts: List[SubsequentPart]
    ): CompilationResult[PartInterpreterType] = {
      compilePartInvokers(parts).map(_.map { partsInvokers => (ctx: DataBatch) =>
        {
          Monoid.combineAll(ctx.map(invokeInterpreterOnContext(node))).flatMap { results =>
            passingErrors[InterpretationResult, PartResult](
              results,
              successful => {
                Monoid.combineAll(successful.orderedGroupBy(_.reference).map { case (pr, ir) =>
                  interpretationInvoke(partsInvokers)(pr, ir)
                })
              }
            )
          }
        }
      })
    }

    private def invokeInterpreterOnContext(node: Node)(ctx: Context): F[ResultType[InterpretationResult]] = {
      processCompilerData.interpreter
        .interpret[F](node, processCompilerData.jobData, ctx, ServiceExecutionContext(ec))
        .map(listOfResults => {
          val results = listOfResults.collect { case Left(value) =>
            value
          }
          val errors = listOfResults.collect { case Right(value) =>
            value
          }
          Writer(errors, results)
        })
    }

    private def interpretationInvoke(
        partInvokers: Map[String, PartInterpreterType]
    )(pr: PartReference, irs: List[InterpretationResult]): InterpreterOutputType = {
      val results: InterpreterOutputType = pr match {
        case _: DeadEndReference =>
          Monad[F].pure[ResultType[PartResult]](Writer.value(Nil))
        case NextPartReference(id) =>
          partInvokers.getOrElse(id, throw new Exception("Unknown reference"))(DataBatch(irs.map(_.finalContext)))
        case r: JoinReference =>
          Monad[F].pure[ResultType[PartResult]](Writer.value(irs.map(ir => JoinResult(r, ir.finalContext))))
        case er: EndingReference => // FIXME: do we need it at all
          Monad[F].pure[ResultType[PartResult]](
            Writer.value(irs.map(ir => EndPartResult(er.nodeId, ir.finalContext, null.asInstanceOf[Res])))
          )
      }
      results
    }

    // First we compute scenario parts compiled so far. Then we search for JoinResults and invoke joinPart.
    // Due to PartSort.sort done in ProcessCompilerBase.compileSources we have guarantee that join part will be put in order after parts that that references to them (in branches).
    // Still, because of possibility to create nested/parallel unions, we have to invoke currently iterated join only for part results that refer to current join,
    // and pass other results further (they should be caught by joins which were put later due to topological sort).
    private def computeNextJoinInvocation(
        computedInterpreter: ScenarioInterpreterType,
        currentNodeId: NodeId,
        joinPartToInvoke: JoinDataBatch => InterpreterOutputType
    ): ScenarioInterpreterType = { (inputBatch: ScenarioInputBatch[Input]) =>
      {
        computedInterpreter(inputBatch).flatMap { results =>
          passingErrors[PartResult, PartResult](
            results,
            successes => {
              val resultsAccordingToCurrentNode = partitionPartResultsAccordingToCurrentNode(successes, currentNodeId)
              val resultsToPassThrough: ResultType[PartResult] =
                Writer(
                  Nil,
                  resultsAccordingToCurrentNode.resultPointingToOthers ++ resultsAccordingToCurrentNode.endResults
                )
              resultsAccordingToCurrentNode.resultPointingToThis match {
                case Nil =>
                  Monad[F].pure(
                    resultsToPassThrough
                  ) // The join was called, but not one that was expected - skip invocation
                case results =>
                  val branchesToInvoke = results.map { p => (BranchId(p.reference.branchId), p.context) }
                  joinPartToInvoke(JoinDataBatch(branchesToInvoke)).map(_ |+| resultsToPassThrough)
              }
            }
          )
        }
      }
    }

    private def partitionPartResultsAccordingToCurrentNode(
        partResults: List[PartResult],
        currentNodeId: NodeId
    ): PartsComputedToPassAndRun = {
      val resultPointingToThis: List[JoinResult] = partResults.collect {
        case e: JoinResult if e.reference.joinId == currentNodeId.value => e
      }
      val resultPointingToOthers: List[PartResult] = partResults.collect {
        case e: JoinResult if e.reference.joinId != currentNodeId.value => e
      }
      val endResults: List[PartResult] = partResults.collect { case e: EndPartResult[Res @unchecked] => e }
      PartsComputedToPassAndRun(resultPointingToThis, resultPointingToOthers, endResults)
    }

    private def compileSource(
        sourcePart: SourcePart
    ): ValidatedNel[ProcessCompilationError, Input => ValidatedNel[ErrorType, Context]] = {
      val SourcePart(sourceObj, node, _, _, _) = sourcePart
      val nodeId                               = node.id
      val validatedSource = (sourceObj, node.data) match {
        case (s: LiteSource[Input @unchecked], _) => Valid(s)
        // Used only in fragment testing, when FragmentInputDefinition is available
        case (_: Source, _: FragmentInputDefinition) if runtimeMode == RuntimeMode.Test =>
          sourceForFragmentInputTesting
        case _ => Invalid(NonEmptyList.of(UnsupportedPart(nodeId, node.data.name)))
      }
      validatedSource.map { source =>
        source.createTransformation(customComponentContext(nodeId, node.data.name))
      }
    }

    private val sourceForFragmentInputTesting: Valid[LiteSource[Input]] =
      Valid(
        new LiteSource[Input] {

          override def createTransformation[T[_]: Monad](
              evaluateLazyParameter: CustomComponentContext[T]
          ): Input => ValidatedNel[ErrorType, Context] = { input =>
            Valid(
              Context(
                ContextId(processCompilerData.jobData.metaData.name, evaluateLazyParameter.nodeId, 0, 0),
                input.asInstanceOf[Map[String, Any]],
                None,
                None,
              )
            )
          }

        }
      )

    private def compileJoinTransformer(
        customNodePart: CustomNodePart
    ): JoinCompilationResult[JoinDataBatch => InterpreterOutputType] = {
      val CustomNodePart(transformerObj, _, validationContext, outputs) = customNodePart
      val mainOutput                                                    = outputs.head
      val node                                                          = mainOutput.node
      val nodeId                                                        = node.id
      val validatedTransformer = validateTransformer(nodeId, node.data.name, outputs) {
        case t: LiteJoinCustomComponent                               => t
        case JoinContextTransformation(_, t: LiteJoinCustomComponent) => t
      }(transformerObj)
      val transformationResult = validatedTransformer.andThen { transformer =>
        val result = compileWithCompilationErrors(node, validationContext).andThen(partInvoker(_, mainOutput.nextParts))
        result.map(rs => rs.map(transformer.createTransformation(_, customComponentContext(nodeId, node.data.name))))
      }
      JoinCompilationResult(nodeId, transformationResult)
    }

    // TODO: this is too complex, rewrite F[ResultType[T]] => WriterT[F, List[ErrorType], List[T]]
    private def passingErrors[In, Out](
        list: ResultType[In],
        action: List[In] => F[ResultType[Out]]
    ): F[ResultType[Out]] = {
      WriterT(Monad[F].pure(list.run))
        .flatMap(l => WriterT(action(l).map(_.run)))
        .run
        .map(k => Writer.apply(k._1, k._2))
    }

  }

  private sealed trait PartResult

  private case class EndPartResult[Result](nodeId: String, context: Context, result: Result) extends PartResult

  private case class JoinResult(reference: JoinReference, context: Context) extends PartResult

}
