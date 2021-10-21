package pl.touk.nussknacker.engine.baseengine

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import cats.implicits._
import cats.{MonadError, Monoid}
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.async.DefaultAsyncInterpretationValueDeterminer
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{NodeId, UnsupportedPart}
import pl.touk.nussknacker.engine.api.context.{JoinContextTransformation, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, RunMode, Source}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.baseengine.api.commonTypes.{DataBatch, ResultType, monoid}
import pl.touk.nussknacker.engine.baseengine.api.customComponentTypes._
import pl.touk.nussknacker.engine.baseengine.api.interpreterTypes.{EndResult, ScenarioInputBatch, ScenarioInterpreter, SourceId}
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.{EngineRuntimeContext, RuntimeContextLifecycle}
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.compiledgraph.CompiledProcessParts
import pl.touk.nussknacker.engine.compiledgraph.node.Node
import pl.touk.nussknacker.engine.compiledgraph.part._
import pl.touk.nussknacker.engine.definition.{CompilerLazyParameterInterpreter, LazyInterpreterDependencies, ProcessDefinitionExtractor}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.resultcollector.ResultCollector
import pl.touk.nussknacker.engine.split.{NodesCollector, ProcessSplitter}
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.nussknacker.engine.{ModelData, compiledgraph}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

object ScenarioInterpreterFactory {

  type ScenarioInterpreterWithLifecycle[F[_], Res <: AnyRef] = ScenarioInterpreter[F, Res] with RuntimeContextLifecycle

  //types of data produced in sinks
  private type WithSinkTypes[K] = Writer[Map[NodeId, TypingResult], K]

  private type InterpreterOutputType[F[_]] = F[ResultType[PartResult]]

  private type ScenarioInterpreterType[F[_]] = ScenarioInputBatch => InterpreterOutputType[F]


  def createInterpreter[F[_], Res <: AnyRef](process: EspProcess, modelData: ModelData,
                                             additionalListeners: List[ProcessListener], resultCollector: ResultCollector, runMode: RunMode)
                                            (implicit ec: ExecutionContext, shape: InterpreterShape[F], capabilityTransformer: CapabilityTransformer[F])
  : ValidatedNel[ProcessCompilationError, ScenarioInterpreterWithLifecycle[F, Res]] = modelData.withThisAsContextClassLoader {

    implicit val monad: MonadError[F, Throwable] = shape.monadError

    val creator = modelData.configCreator
    val processObjectDependencies = ProcessObjectDependencies(modelData.processConfig, modelData.objectNaming)

    val definitions = ProcessDefinitionExtractor.extractObjectWithMethods(creator, processObjectDependencies)
    val listeners = creator.listeners(processObjectDependencies) ++ additionalListeners

    val compilerData = ProcessCompilerData.prepare(process,
      definitions,
      listeners,
      modelData.modelClassLoader.classLoader, resultCollector,
      runMode
      // defaultAsyncValue is not important here because it isn't used in base mode (??)
    )(DefaultAsyncInterpretationValueDeterminer.DefaultValue)

    compilerData.compile().andThen { compiledProcess =>
      val sources = extractSource(compiledProcess)

      val nodesUsed = NodesCollector.collectNodesInAllParts(ProcessSplitter.split(process).sources).map(_.data)
      val lifecycle = compilerData.lifecycle(nodesUsed)
      InvokerCompiler(compiledProcess, compilerData, runMode, capabilityTransformer).compile.map(_.run).map { case (sinkTypes, invoker) =>
        ScenarioInterpreterImpl(sources, sinkTypes, invoker, lifecycle, modelData)
      }
    }
  }

  private def extractSource(compiledProcess: CompiledProcessParts): Map[SourceId, Source[Any]] = compiledProcess.sources.collect {
    case a: SourcePart => (SourceId(a.id), a.obj)
  }.toMap

  private case class ScenarioInterpreterImpl[F[_], Res <: AnyRef](sources: Map[SourceId, Source[Any]],
                                                          sinkTypes: Map[NodeId, TypingResult],
                                                          private val invoker: ScenarioInterpreterType[F],
                                                          private val lifecycle: Seq[Lifecycle],
                                                          private val modelData: ModelData
                                                         )(implicit monad: MonadError[F, Throwable]) extends ScenarioInterpreter[F, Res] with RuntimeContextLifecycle {

    def invoke(contexts: ScenarioInputBatch): F[ResultType[EndResult[Res]]] = modelData.withThisAsContextClassLoader {
      invoker(contexts).map { result =>
        result.map(_.map {
          case e: EndPartResult[Res@unchecked] => EndResult(NodeId(e.nodeId), e.context, e.result)
          case other => throw new IllegalArgumentException(s"Should not happen, $other left in results")
        })
      }
    }

    def open(jobData: JobData, context: EngineRuntimeContext): Unit = modelData.withThisAsContextClassLoader {
      lifecycle.foreach {
        case a: RuntimeContextLifecycle => a.open(jobData, context)
        case a => a.open(jobData)
      }
    }

    def close(): Unit = modelData.withThisAsContextClassLoader {
      lifecycle.foreach(_.close())
    }

  }

  private case class InvokerCompiler[F[_], Res <: AnyRef](compiledProcess: CompiledProcessParts, processCompilerData: ProcessCompilerData,
                                                          runMode: RunMode, capabilityTransformer: CapabilityTransformer[F])
                                                         (implicit ec: ExecutionContext, shape: InterpreterShape[F]) {
    //we collect errors and also typing results of sinks
    type CompilationResult[K] = ValidatedNel[ProcessCompilationError, WithSinkTypes[K]]

    private type InterpreterOutputType = F[ResultType[PartResult]]
    
    private type ScenarioInterpreterType = ScenarioInputBatch => InterpreterOutputType

    private type PartInterpreterType = DataBatch => InterpreterOutputType

    private implicit val monad: MonadError[F, Throwable] = shape.monadError

    private val lazyParameterInterpreter: CompilerLazyParameterInterpreter = new CompilerLazyParameterInterpreter {
      override def deps: LazyInterpreterDependencies = processCompilerData.lazyInterpreterDeps

      override def metaData: MetaData = processCompilerData.metaData

      override def close(): Unit = {}
    }

    def compile: CompilationResult[ScenarioInterpreterType] = {
      val emptyPartInvocation: ScenarioInterpreterType = (inputs: ScenarioInputBatch) =>
        Monoid.combineAll(inputs.value.map {
          case (source, ctx) => monad.pure[ResultType[PartResult]](Writer(EspExceptionInfo(Some(source.value),
            new IllegalArgumentException(s"Unknown source ${source.value}"), ctx) :: Nil, Nil))
        })

      def computeNextSourceInvocation(base: ScenarioInterpreterType, nextSource: SourcePart, next: PartInterpreterType) = (inputs: ScenarioInputBatch) =>
        Monoid.combineAll(inputs.value.map {
          case (source, ctx) if source.value == nextSource.id => next(DataBatch(ctx))
          case other => base(ScenarioInputBatch(other :: Nil))
        })

      //here we rely on the fact that parts are sorted correctly (see ProcessCompiler.compileSources)
      compiledProcess.sources.foldLeft[CompilationResult[ScenarioInterpreterType]](Valid(Writer(Map.empty[NodeId, TypingResult], emptyPartInvocation))) {
        case (resultSoFar, join: CustomNodePart) =>
          val compiledTransformer = compileJoinTransformer(join)
          resultSoFar.product(compiledTransformer).map { case (WriterT((types, interpreterMap)), WriterT((types2, part))) =>
            val result = computeNextJoinInvocation(interpreterMap, part)
            Writer(types ++ types2, result)
          }
        case (resultSoFar, a: SourcePart) =>
          resultSoFar.product(compiledPartInvoker(a)).map { case (WriterT((types, interpreter)), WriterT((types2, part))) =>
            Writer(types ++ types2, computeNextSourceInvocation(interpreter, a, part))
          }
      }
    }

    private def compileWithCompilationErrors(node: SplittedNode[_], validationContext: ValidationContext): ValidatedNel[ProcessCompilationError, Node] =
      processCompilerData.subPartCompiler.compile(node, validationContext)(compiledProcess.metaData).result

    private def customComponentContext(nodeId: String) = CustomComponentContext[F](nodeId, lazyParameterInterpreter, capabilityTransformer)

    private def compiledPartInvoker(processPart: ProcessPart): CompilationResult[PartInterpreterType] = processPart match {
      case SourcePart(_, node, validationContext, nextParts, _) =>
        compileWithCompilationErrors(node, validationContext).andThen(partInvoker(_, nextParts))
      case SinkPart(sink, endNode, _, validationContext) =>
        compileWithCompilationErrors(endNode, validationContext).andThen { compiled =>
          partInvoker(compiled, List()).map(prepareResponse(compiled, sink))
        }
      case CustomNodePart(transformerObj, node, _, validationContext, parts, _) =>
        val validatedTransformer = transformerObj match {
          case t: CustomBaseEngineComponent => Valid(t)
          case _ => Invalid(NonEmptyList.of(UnsupportedPart(node.id)))
        }
        validatedTransformer.andThen { transformer =>
          val result = compileWithCompilationErrors(node, validationContext).andThen(partInvoker(_, parts))
          result.map(rs => rs.map(transformer.createTransformation(_, customComponentContext(node.id))))
        }
    }

    private def prepareResponse(compiledNode: Node, sink: process.Sink)(it: WithSinkTypes[PartInterpreterType]): WithSinkTypes[PartInterpreterType] = sink match {
      case sinkWithParams: BaseEngineSink[Res@unchecked] =>
        val (returnType, evaluation) = sinkWithParams.createTransformation[F](customComponentContext(compiledNode.id))

        it.bimap(_.updated(NodeId(compiledNode.id), returnType), (originalSink: PartInterpreterType) => (ctxs: DataBatch) => {
          //we invoke 'original sink part' because otherwise listeners wouldn't work properly
          originalSink.apply(ctxs).flatMap { _ =>
            evaluation(ctxs).map(_.map(_.map {
              case (ctx, result) => EndPartResult(compiledNode.id, ctx, result)
            }))
          }
        })

      case other => throw new IllegalArgumentException(s"Not supported sink: $other")
    }

    private def compilePartInvokers(parts: List[SubsequentPart]): CompilationResult[Map[String, PartInterpreterType]] =
      parts.map(part => compiledPartInvoker(part).map(compiled => part.id -> compiled))
        .sequence.map { res =>
        Writer(res.flatMap(_._2.written).toMap, res.toMap.mapValues(_.value))
      }

    private def partInvoker(node: compiledgraph.node.Node, parts: List[SubsequentPart]): CompilationResult[PartInterpreterType] = {
      compilePartInvokers(parts).map(_.map { partsInvokers =>
        (ctx: DataBatch) => {
          Monoid.combineAll(ctx.map(invokeInterpreterOnContext(node))).flatMap { results =>
            passingErrors[InterpretationResult, PartResult](results, successful => {
              Monoid.combineAll(successful.groupBy(_.reference).map {
                case (pr, ir) => interpretationInvoke(partsInvokers)(pr, ir)
              })
            })
          }
        }
      })
    }

    private def invokeInterpreterOnContext(node: Node)(ctx: Context): F[ResultType[InterpretationResult]] = {
      implicit val implicitRunMode: RunMode = runMode
      val interpreterOut = processCompilerData.interpreter.interpret[F](node, processCompilerData.metaData, ctx)
      interpreterOut.map {
        case Left(outputs) => Writer.value(outputs)
        case Right(value) => Writer(value :: Nil, Nil)
      }
    }

    private def interpretationInvoke(partInvokers: Map[String, PartInterpreterType])
                                    (pr: PartReference, irs: List[InterpretationResult]): InterpreterOutputType = {
      val results: InterpreterOutputType = pr match {
        case er: EndReference =>
          //FIXME: do we need it at all
          monad.pure[ResultType[PartResult]](Writer.value(irs.map(ir => EndPartResult(er.nodeId, ir.finalContext, ir.output.asInstanceOf[Res]))))
        case _: DeadEndReference =>
          monad.pure[ResultType[PartResult]](Writer.value(Nil))
        case r: JoinReference =>
          monad.pure[ResultType[PartResult]](Writer.value(irs.map(ir => JoinResult(r, ir.finalContext))))
        case NextPartReference(id) =>
          partInvokers.getOrElse(id, throw new Exception("Unknown reference"))(DataBatch(irs.map(_.finalContext)))
      }
      results
    }

    //First we compute scenario parts compiled so far. Then we search for JoinResults and invoke joinPart
    //We know that we'll find all results pointing to join, because we sorted the parts
    private def computeNextJoinInvocation(computedInterpreter: ScenarioInterpreterType,
                                          joinPartToInvoke: JoinDataBatch => InterpreterOutputType): ScenarioInterpreterType = {
      (ctxs: ScenarioInputBatch) => {
        computedInterpreter(ctxs).flatMap { results =>
          passingErrors[PartResult, PartResult](results, successes => {
            val resultsPointingToJoin = JoinDataBatch(successes
              .collect { case e: JoinResult => (BranchId(e.reference.branchId), e.context) })
            val endResults: ResultType[PartResult] = Writer(Nil, successes.collect { case e: EndPartResult[Res@unchecked] => e })
            joinPartToInvoke(resultsPointingToJoin).map(_ |+| endResults)
          })
        }
      }
    }

    private def compileJoinTransformer(customNodePart: CustomNodePart): CompilationResult[JoinDataBatch => InterpreterOutputType] = {
      val CustomNodePart(transformerObj, node, _, validationContext, parts, _) = customNodePart
      val validatedTransformer = transformerObj match {
        case t: JoinCustomBaseEngineComponent => Valid(t)
        case JoinContextTransformation(_, t: JoinCustomBaseEngineComponent) => Valid(t)
        case _ => Invalid(NonEmptyList.of(UnsupportedPart(node.id)))
      }
      validatedTransformer.andThen { transformer =>
        val result = compileWithCompilationErrors(node, validationContext).andThen(partInvoker(_, parts))
        result.map(rs => rs.map(transformer.createTransformation(_, customComponentContext(node.id))))
      }
    }

    //TODO: this is too complex, rewrite F[ResultType[T]] => WriterT[F, List[ErrorType], List[T]]
    private def passingErrors[In, Out](list: ResultType[In], action: List[In] => F[ResultType[Out]]): F[ResultType[Out]] = {
      WriterT(monad.pure(list.run))
        .flatMap(l => WriterT(action(l).map(_.run)))
        .run.map(k => Writer.apply(k._1, k._2))
    }
  }

  private sealed trait PartResult

  private case class EndPartResult[Result](nodeId: String, context: Context, result: Result) extends PartResult

  private case class JoinResult(reference: JoinReference, context: Context) extends PartResult

}


