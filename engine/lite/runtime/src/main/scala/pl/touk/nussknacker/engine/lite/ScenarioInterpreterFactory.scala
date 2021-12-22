package pl.touk.nussknacker.engine.lite

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import cats.implicits._
import cats.{Monad, Monoid}
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.async.DefaultAsyncInterpretationValueDeterminer
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{NodeId, UnsupportedPart}
import pl.touk.nussknacker.engine.api.context.{JoinContextTransformation, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, RunMode, Source}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.lite.api.commonTypes.{DataBatch, ResultType, monoid}
import pl.touk.nussknacker.engine.lite.api.customComponentTypes._
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{EndResult, ScenarioInputBatch, ScenarioInterpreter, SourceId}
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.compiledgraph.CompiledProcessParts
import pl.touk.nussknacker.engine.compiledgraph.node.Node
import pl.touk.nussknacker.engine.compiledgraph.part._
import pl.touk.nussknacker.engine.definition.{CompilerLazyParameterInterpreter, LazyInterpreterDependencies, ProcessDefinitionExtractor}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.api.commonTypes.{DataBatch, ErrorType, ResultType, monoid}
import pl.touk.nussknacker.engine.lite.api.customComponentTypes._
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{EndResult, ScenarioInputBatch, ScenarioInterpreter, SourceId}
import pl.touk.nussknacker.engine.resultcollector.{ProductionServiceInvocationCollector, ResultCollector}
import pl.touk.nussknacker.engine.split.{NodesCollector, ProcessSplitter}
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.nussknacker.engine.util.metrics.common.{EndCountingListener, ExceptionCountingListener, NodeCountingListener}
import pl.touk.nussknacker.engine.{ModelData, compiledgraph}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

object ScenarioInterpreterFactory {

  type ScenarioInterpreterWithLifecycle[F[_], Input, Res <: AnyRef] = ScenarioInterpreter[F, Input, Res] with Lifecycle

  //types of data produced in sinks
  private type WithSinkTypes[K] = Writer[Map[NodeId, TypingResult], K]

  private type InterpreterOutputType[F[_]] = F[ResultType[PartResult]]

  private type ScenarioInterpreterType[F[_], Input] = ScenarioInputBatch[Input] => InterpreterOutputType[F]

  def createInterpreter[F[_], Input, Res <: AnyRef](process: EspProcess,
                                                    modelData: ModelData,
                                                    additionalListeners: List[ProcessListener] = Nil,
                                                    resultCollector: ResultCollector = ProductionServiceInvocationCollector,
                                                    runMode: RunMode = RunMode.Normal)
                                                   (implicit ec: ExecutionContext, shape: InterpreterShape[F], capabilityTransformer: CapabilityTransformer[F])
  : ValidatedNel[ProcessCompilationError, ScenarioInterpreterWithLifecycle[F, Input, Res]] = modelData.withThisAsContextClassLoader {

    implicit val monad: Monad[F] = shape.monad

    val creator = modelData.configCreator
    val processObjectDependencies = ProcessObjectDependencies(modelData.processConfig, modelData.objectNaming)

    val definitions = ProcessDefinitionExtractor.extractObjectWithMethods(creator, processObjectDependencies)

    val countingListeners = List(new NodeCountingListener, new ExceptionCountingListener, new EndCountingListener)
    val listeners = creator.listeners(processObjectDependencies) ++ additionalListeners ++ countingListeners

    val compilerData = ProcessCompilerData.prepare(process,
      definitions,
      listeners,
      modelData.modelClassLoader.classLoader, resultCollector,
      runMode
      // defaultAsyncValue is not important here because it isn't used in base mode (??)
    )(DefaultAsyncInterpretationValueDeterminer.DefaultValue)

    compilerData.compile().andThen { compiledProcess =>
      val components = extractComponents(compiledProcess.sources.toList)
      val sources = collectSources(components)

      val nodesUsed = NodesCollector.collectNodesInAllParts(ProcessSplitter.split(process).sources).map(_.data)
      val lifecycle = compilerData.lifecycle(nodesUsed) ++ components.values.collect {
        case lifecycle: Lifecycle => lifecycle
      }
      InvokerCompiler[F, Input, Res](compiledProcess, compilerData, runMode, capabilityTransformer).compile.map(_.run).map { case (sinkTypes, invoker) =>
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

  private case class ScenarioInterpreterImpl[F[_], Input, Res <: AnyRef](sources: Map[SourceId, Source],
                                                                         sinkTypes: Map[NodeId, TypingResult],
                                                                         private val invoker: ScenarioInterpreterType[F, Input],
                                                                         private val lifecycle: Seq[Lifecycle],
                                                                         private val modelData: ModelData
                                                                        )(implicit monad: Monad[F]) extends ScenarioInterpreter[F, Input, Res] with Lifecycle {

    def invoke(contexts: ScenarioInputBatch[Input]): F[ResultType[EndResult[Res]]] = modelData.withThisAsContextClassLoader {
      invoker(contexts).map { result =>
        result.map(_.map {
          case e: EndPartResult[Res@unchecked] => EndResult(NodeId(e.nodeId), e.context, e.result)
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

  private case class InvokerCompiler[F[_], Input, Res <: AnyRef](compiledProcess: CompiledProcessParts, processCompilerData: ProcessCompilerData,
                                                                 runMode: RunMode, capabilityTransformer: CapabilityTransformer[F])
                                                                (implicit ec: ExecutionContext, shape: InterpreterShape[F]) {
    //we collect errors and also typing results of sinks
    type CompilationResult[K] = ValidatedNel[ProcessCompilationError, WithSinkTypes[K]]

    private type InterpreterOutputType = F[ResultType[PartResult]]
    
    private type ScenarioInterpreterType = ScenarioInputBatch[Input] => InterpreterOutputType

    private type PartInterpreterType = DataBatch => InterpreterOutputType

    private implicit val monad: Monad[F] = shape.monad

    private val lazyParameterInterpreter: CompilerLazyParameterInterpreter = new CompilerLazyParameterInterpreter {
      override def deps: LazyInterpreterDependencies = processCompilerData.lazyInterpreterDeps

      override def metaData: MetaData = processCompilerData.metaData

      override def close(): Unit = {}
    }

    def compile: CompilationResult[ScenarioInterpreterType] = {
      val emptyPartInvocation: ScenarioInterpreterType = (inputs: ScenarioInputBatch[Input]) =>
        Monoid.combineAll(inputs.value.map {
          case (source, input) => monad.pure[ResultType[PartResult]](Writer(NuExceptionInfo(Some(source.value),
            new IllegalArgumentException(s"Unknown source ${source.value}"), Context("")) :: Nil, Nil))
        })

      def computeNextSourceInvocation(base: ScenarioInterpreterType, nextSource: SourcePart, next: Input => F[ResultType[PartResult]]): ScenarioInputBatch[Input] => InterpreterOutputType = (inputs: ScenarioInputBatch[Input]) =>
        Monoid.combineAll(inputs.value.map {
          case (source, ctx) if source.value == nextSource.id => next(ctx)
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
          resultSoFar.product(compiledPartInvoker(a)).andThen { case (WriterT((types, interpreter)), WriterT((types2, part))) =>
            compileSource(a).map { compiledSource =>
              Writer(types ++ types2, computeNextSourceInvocation(
                  interpreter,
                  a,
                  compiledSource andThen { validatedCtx =>
                    validatedCtx.fold(
                      errs => monad.pure(Writer(errs.toList, List.empty)),
                      ctx => {
                        val outputType = part(DataBatch(List(ctx)))
                        outputType
                      })
                  }))
            }
          }
      }.map(_.map(invokeListenersOnException))
    }

    //TODO: Figure out how to invoke this properly. Solution below works when F.map is invoked
    //only once per computation. We cannot guarantee it...
    private def invokeListenersOnException(scenarioInterpreter: ScenarioInterpreterType): ScenarioInterpreterType = {
      scenarioInterpreter.andThen(_.map {
        case results@WriterT((exceptions, _)) =>
          exceptions.foreach(ex => processCompilerData.listeners.foreach(_.exceptionThrown(ex)))
          results
      })
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
          case t: LiteCustomComponent => Valid(t)
          case _ => Invalid(NonEmptyList.of(UnsupportedPart(node.id)))
        }
        validatedTransformer.andThen { transformer =>
          val result = compileWithCompilationErrors(node, validationContext).andThen(partInvoker(_, parts))
          result.map(rs => rs.map(transformer.createTransformation(_, customComponentContext(node.id))))
        }
    }

    private def prepareResponse(compiledNode: Node, sink: process.Sink)(it: WithSinkTypes[PartInterpreterType]): WithSinkTypes[PartInterpreterType] = sink match {
      case sinkWithParams: LiteSink[Res@unchecked] =>
        val (returnType, evaluation) = sinkWithParams.createTransformation[F](customComponentContext(compiledNode.id))

        it.bimap(_.updated(NodeId(compiledNode.id), returnType), (originalSink: PartInterpreterType) => (ctxs: DataBatch) => {
          //we invoke 'original sink part' because otherwise listeners wouldn't work properly
          originalSink.apply(ctxs).flatMap { _ =>
            evaluation(ctxs).map(_.map(_.map {
              case (ctx, result) =>
                val value = EndPartResult(compiledNode.id, ctx, result)
                value
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
      processCompilerData.interpreter
        .interpret[F](node, processCompilerData.metaData, ctx)
        .map(listOfResults => {
          val results = listOfResults.collect {
            case Left(value) => value
          }
          val errors = listOfResults.collect {
            case Right(value) => value
          }
          Writer(errors, results)
        })
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
      (inputBatch: ScenarioInputBatch[Input]) => {
        computedInterpreter(inputBatch).flatMap { results =>
          passingErrors[PartResult, PartResult](results, successes => {
            val resultsPointingToJoin = JoinDataBatch(successes
              .collect { case e: JoinResult => (BranchId(e.reference.branchId), e.context) })
            val endResults: ResultType[PartResult] = Writer(Nil, successes.collect { case e: EndPartResult[Res@unchecked] => e })
            joinPartToInvoke(resultsPointingToJoin).map(_ |+| endResults)
          })
        }
      }
    }

    private def compileSource(sourcePart: SourcePart): ValidatedNel[ProcessCompilationError, Input => ValidatedNel[ErrorType, Context]] = {
      val SourcePart(sourceObj, node, _, _, _) = sourcePart
      val validatedSource = sourceObj match {
        case s: LiteSource[Input@unchecked] => Valid(s)
        case _ => Invalid(NonEmptyList.of(UnsupportedPart(node.id)))
      }
      validatedSource.map { source =>
        source.createTransformation(customComponentContext(node.id))
      }
    }

    private def compileJoinTransformer(customNodePart: CustomNodePart): CompilationResult[JoinDataBatch => InterpreterOutputType] = {
      val CustomNodePart(transformerObj, node, _, validationContext, parts, _) = customNodePart
      val validatedTransformer = transformerObj match {
        case t: LiteJoinCustomComponent => Valid(t)
        case JoinContextTransformation(_, t: LiteJoinCustomComponent) => Valid(t)
        case _ => Invalid(NonEmptyList.of(UnsupportedPart(node.id)))
      }
      validatedTransformer.andThen { transformer =>
        val result = compileWithCompilationErrors(node, validationContext).andThen(partInvoker(_, parts))
        result.map(rs => rs.map(partInterpreterType => {
          val function = transformer.createTransformation(partInterpreterType, customComponentContext(node.id))
          function
        }))
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


