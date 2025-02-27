package pl.touk.nussknacker.engine.lite

import cats.{Monad, Monoid}
import cats.data._
import cats.data.Validated.{Invalid, Valid}
import cats.implicits._
import pl.touk.nussknacker.engine.{compiledgraph, InterpretationResult, ModelData}
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{ComponentType, NodeComponentInfo}
import pl.touk.nussknacker.engine.api.context.{JoinContextTransformation, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.UnsupportedPart
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process.{
  ComponentUseCase,
  ProcessObjectDependencies,
  ServiceExecutionContext,
  Source
}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile._
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
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
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
      modelData: ModelData,
      additionalListeners: List[ProcessListener] = Nil,
      resultCollector: ResultCollector = ProductionServiceInvocationCollector,
      componentUseCase: ComponentUseCase = ComponentUseCase.EngineRuntime
  )(
      implicit ec: ExecutionContext,
      shape: InterpreterShape[F],
      capabilityTransformer: CapabilityTransformer[F]
  ): ValidatedNel[ProcessCompilationError, ScenarioInterpreterWithLifecycle[F, Input, Res]] =
    modelData.withThisAsContextClassLoader {

      val creator           = modelData.configCreator
      val modelDependencies = ProcessObjectDependencies.withConfig(modelData.modelConfig)

      val allNodes = process.collectAllNodes
      val countingListeners = List(
        LoggingListener,
        new NodeCountingListener(allNodes.map(_.id)),
        new ExceptionCountingListener,
        new EndCountingListener(allNodes),
      )
      val listeners = creator.listeners(modelDependencies) ++ additionalListeners ++ countingListeners

      val compilerData = ProcessCompilerData.prepare(
        jobData,
        modelData.modelDefinitionWithClasses,
        modelData.engineDictRegistry,
        listeners,
        modelData.modelClassLoader,
        resultCollector,
        componentUseCase,
        modelData.customProcessValidator
      )

      compilerData.compile(process).andThen { compiledProcess =>
        val components = extractComponents(compiledProcess.sources.toList)
        val sources    = collectSources(components)

        val lifecycle = compilerData.lifecycle(allNodes) ++ components.values.collect { case lifecycle: Lifecycle =>
          lifecycle
        }
        InvokerCompiler[F, Input, Res](
          compiledProcess,
          compilerData,
          componentUseCase,
          capabilityTransformer,
          jobData
        ).compile
          .map(_.run)
          .map { case (sinkTypes, invoker) =>
            ScenarioInterpreterImpl(sources, sinkTypes, invoker, lifecycle, modelData)
          }
      }
    }

  private def extractComponents(parts: List[ProcessPart]): Map[String, Any] = {
    parts.foldLeft(Map.empty[String, Any]) { (acc, part) =>
      part match {
        case source: SourcePart =>
          acc + (source.id -> source.obj) ++ extractComponents(source.nextParts)
        case custom: CustomNodePart =>
          acc + (custom.id -> custom.transformer) ++ extractComponents(custom.nextParts)
        case sink: SinkPart =>
          acc + (sink.id -> sink.obj)
      }
    }
  }

  private def collectSources(componentById: Map[String, Any]): Map[SourceId, Source] = componentById.collect {
    case (id, a: Source) => (SourceId(id), a)
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
      modelData.withThisAsContextClassLoader {
        invoker(contexts).map { result =>
          result.map(_.map {
            case e: EndPartResult[Res @unchecked] => EndResult(NodeId(e.nodeId), e.context, e.result)
            case other => throw new IllegalArgumentException(s"Should not happen, $other left in results")
          })
        }
      }

    override def open(context: EngineRuntimeContext): Unit = modelData.withThisAsContextClassLoader {
      lifecycle.foreach(_.open(context))
    }

    override def close(): Unit = modelData.withThisAsContextClassLoader {
      lifecycle.foreach(_.close())
    }

  }

  private case class InvokerCompiler[F[_]: Monad, Input, Res <: AnyRef](
      compiledProcess: CompiledProcessParts,
      processCompilerData: ProcessCompilerData,
      componentUseCase: ComponentUseCase,
      capabilityTransformer: CapabilityTransformer[F],
      jobData: JobData
  )(implicit ec: ExecutionContext, shape: InterpreterShape[F]) {
    // we collect errors and also typing results of sinks
    type CompilationResult[K] = ValidatedNel[ProcessCompilationError, WithSinkTypes[K]]

    private type InterpreterOutputType = F[ResultType[PartResult]]

    private type ScenarioInterpreterType = ScenarioInputBatch[Input] => InterpreterOutputType

    private type PartInterpreterType = DataBatch => InterpreterOutputType

    def compile: CompilationResult[ScenarioInterpreterType] = {
      val emptyPartInvocation: ScenarioInterpreterType = (inputs: ScenarioInputBatch[Input]) =>
        Monoid.combineAll(inputs.value.map { case (source, input) =>
          Monad[F].pure[ResultType[PartResult]](
            Writer(
              NuExceptionInfo(
                Some(NodeComponentInfo(source.value, ComponentType.Source, "source")),
                new IllegalArgumentException(s"Unknown source ${source.value}"),
                Context("")
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
          case (source, ctx) if source.value == nextSource.id => next(ctx)
          case other                                          => base(ScenarioInputBatch(other :: Nil))
        })

      // here we rely on the fact that parts are sorted correctly (see ProcessCompiler.compileSources)
      compiledProcess.sources
        .foldLeft[CompilationResult[ScenarioInterpreterType]](
          Valid(Writer(Map.empty[NodeId, TypingResult], emptyPartInvocation))
        ) {
          case (resultSoFar, join: CustomNodePart) =>
            val compiledTransformer = compileJoinTransformer(join)
            resultSoFar.product(compiledTransformer).map {
              case (WriterT((types, interpreterMap)), WriterT((types2, part))) =>
                val result = computeNextJoinInvocation(interpreterMap, part)
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
      processCompilerData.subPartCompiler.compile(node, validationContext)(jobData).result

    private def customComponentContext(nodeId: String) =
      CustomComponentContext[F](nodeId, capabilityTransformer)

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
        case CustomNodePart(transformerObj, node, _, validationContext, parts, _) =>
          val validatedTransformer = transformerObj match {
            case t: LiteCustomComponent => Valid(t)
            case _                      => Invalid(NonEmptyList.of(UnsupportedPart(node.id)))
          }
          validatedTransformer.andThen { transformer =>
            val result = compileWithCompilationErrors(node, validationContext).andThen(partInvoker(_, parts))
            result.map(rs => rs.map(transformer.createTransformation(_, customComponentContext(node.id))))
          }
      }

    private def prepareResponse(compiledNode: Node, sink: process.Sink)(
        it: WithSinkTypes[PartInterpreterType]
    ): WithSinkTypes[PartInterpreterType] = sink match {
      case sinkWithParams: LiteSink[Res @unchecked] =>
        val (returnType, evaluation) = sinkWithParams.createTransformation[F](customComponentContext(compiledNode.id))

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
      parts.map(part => compiledPartInvoker(part).map(compiled => part.id -> compiled)).sequence.map { res =>
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
                Monoid.combineAll(successful.groupBy(_.reference).map { case (pr, ir) =>
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
        .interpret[F](node, jobData, ctx, ServiceExecutionContext(ec))
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

    // First we compute scenario parts compiled so far. Then we search for JoinResults and invoke joinPart
    // We know that we'll find all results pointing to join, because we sorted the parts
    private def computeNextJoinInvocation(
        computedInterpreter: ScenarioInterpreterType,
        joinPartToInvoke: JoinDataBatch => InterpreterOutputType
    ): ScenarioInterpreterType = { (inputBatch: ScenarioInputBatch[Input]) =>
      {
        computedInterpreter(inputBatch).flatMap { results =>
          passingErrors[PartResult, PartResult](
            results,
            successes => {
              val resultsPointingToJoin = JoinDataBatch(
                successes
                  .collect { case e: JoinResult => (BranchId(e.reference.branchId), e.context) }
              )
              val endResults: ResultType[PartResult] =
                Writer(Nil, successes.collect { case e: EndPartResult[Res @unchecked] => e })
              joinPartToInvoke(resultsPointingToJoin).map(_ |+| endResults)
            }
          )
        }
      }
    }

    private def compileSource(
        sourcePart: SourcePart
    ): ValidatedNel[ProcessCompilationError, Input => ValidatedNel[ErrorType, Context]] = {
      val SourcePart(sourceObj, node, _, _, _) = sourcePart
      val validatedSource = (sourceObj, node.data) match {
        case (s: LiteSource[Input @unchecked], _) => Valid(s)
        // Used only in fragment testing, when FragmentInputDefinition is available
        case (_: Source, fragmentInputDef: FragmentInputDefinition)
            if componentUseCase == ComponentUseCase.TestRuntime =>
          sourceForFragmentInputTestng(fragmentInputDef)
        case _ => Invalid(NonEmptyList.of(UnsupportedPart(node.id)))
      }
      validatedSource.map { source =>
        source.createTransformation(customComponentContext(node.id))
      }
    }

    private def sourceForFragmentInputTestng(fragmentInputDef: FragmentInputDefinition): Valid[LiteSource[Input]] =
      Valid(
        new LiteSource[Input] {

          override def createTransformation[F[_]: Monad](
              evaluateLazyParameter: CustomComponentContext[F]
          ): Input => ValidatedNel[ErrorType, Context] = { input =>
            Valid(Context(fragmentInputDef.id, input.asInstanceOf[Map[String, Any]], None))
          }

        }
      )

    private def compileJoinTransformer(
        customNodePart: CustomNodePart
    ): CompilationResult[JoinDataBatch => InterpreterOutputType] = {
      val CustomNodePart(transformerObj, node, _, validationContext, parts, _) = customNodePart
      val validatedTransformer = transformerObj match {
        case t: LiteJoinCustomComponent                               => Valid(t)
        case JoinContextTransformation(_, t: LiteJoinCustomComponent) => Valid(t)
        case _ => Invalid(NonEmptyList.of(UnsupportedPart(node.id)))
      }
      validatedTransformer.andThen { transformer =>
        val result = compileWithCompilationErrors(node, validationContext).andThen(partInvoker(_, parts))
        result.map(rs => rs.map(transformer.createTransformation(_, customComponentContext(node.id))))
      }
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
