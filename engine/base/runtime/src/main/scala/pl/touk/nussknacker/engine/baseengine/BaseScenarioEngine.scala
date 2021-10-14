package pl.touk.nussknacker.engine.baseengine

import cats.{Id, MonadError}
import cats.data.Validated.{Invalid, Valid}
import cats.data._
import cats.implicits.toFunctorOps
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.api.async.DefaultAsyncInterpretationValueDeterminer
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.UnsupportedPart
import pl.touk.nussknacker.engine.api.context.{JoinContextTransformation, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, RunMode, Source}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.baseengine.api.BaseScenarioEngineTypes._
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.{RuntimeContext, RuntimeContextLifecycle, RuntimeContextPreparer}
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.compiledgraph.CompiledProcessParts
import pl.touk.nussknacker.engine.compiledgraph.node.Node
import pl.touk.nussknacker.engine.compiledgraph.part._
import pl.touk.nussknacker.engine.definition.{CompilerLazyParameterInterpreter, LazyInterpreterDependencies, ProcessDefinitionExtractor}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.resultcollector.ResultCollector
import pl.touk.nussknacker.engine.split.{NodesCollector, ProcessSplitter}
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.nussknacker.engine.baseengine.metrics.InvocationMetrics
import pl.touk.nussknacker.engine.{ModelData, compiledgraph}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds
import scala.util.control.NonFatal

trait BaseScenarioEngine[F[_], Res <: AnyRef] {

  type WithSinkTypes[K] = Writer[Map[String, TypingResult], K]

  type InternalInterpreterOutputType = F[ResultType[PartResult]]

  type ScenarioInternalInterpreterType = List[(SourceId, Context)] => F[ResultType[PartResult]]

  def createInterpreter(process: EspProcess, contextPreparer: RuntimeContextPreparer, modelData: ModelData,
                        additionalListeners: List[ProcessListener], resultCollector: ResultCollector, runMode: RunMode)
                       (implicit ec: ExecutionContext, shape: InterpreterShape[F])
  : ValidatedNel[ProcessCompilationError, ScenarioInterpreter] = modelData.withThisAsContextClassLoader {

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
      InvokerCompiler(compiledProcess, compilerData, runMode).compile.map(_.run).map { case (sinkTypes, invoker) =>
        ScenarioInterpreter(sources, sinkTypes, contextPreparer.prepare(process.id), invoker, lifecycle, modelData)
      }
    }
  }

  private def extractSource(compiledProcess: CompiledProcessParts): Map[SourceId, Source[Any]] = compiledProcess.sources.collect {
    case a: SourcePart => (SourceId(a.id), a.obj)
  }.toMap

  case class ScenarioInterpreter(sources: Map[SourceId, Source[Any]],
                                 sinkTypes: Map[String, TypingResult],
                                 context: RuntimeContext,
                                 private val invoker: ScenarioInternalInterpreterType,
                                 private val lifecycle: Seq[Lifecycle],
                                 private val modelData: ModelData
                                )(implicit monad: MonadError[F, Throwable]) extends InvocationMetrics with AutoCloseable {

    val id: String = context.processId

    def invoke(contexts: List[(SourceId, Context)]): F[ResultType[EndResult[Res]]] = modelData.withThisAsContextClassLoader {
      invoker(contexts).map { result =>
        result.map(_.map {
          case e: EndResult[Res@unchecked] => e
          case other => throw new IllegalArgumentException(s"Should not happen, $other left in results")
        })
      }
    }

    def open(jobData: JobData): Unit = modelData.withThisAsContextClassLoader {
      lifecycle.foreach {
        case a: RuntimeContextLifecycle => a.open(jobData, context)
        case a => a.open(jobData)
      }
    }

    def close(): Unit = modelData.withThisAsContextClassLoader {
      lifecycle.foreach(_.close())
      context.close()
    }

  }

  private case class InvokerCompiler(compiledProcess: CompiledProcessParts, processCompilerData: ProcessCompilerData, runMode: RunMode)
                                    (implicit ec: ExecutionContext, shape: InterpreterShape[F]) {

    implicit val monad: MonadError[F, Throwable] = shape.monadError

    import cats.implicits._

    type CompilationResult[K] = ValidatedNel[ProcessCompilationError, K]

    def compile: CompilationResult[WithSinkTypes[ScenarioInternalInterpreterType]] = {
      //here we rely on the fact that parts are sorted correctly (see ProcessCompiler.compileSources)
      //this guarantess that SourcePart is first
      //val NonEmptyList(start, rest) = compiledProcess.sources

      val baseFunction: ScenarioInternalInterpreterType = (inputs: List[(SourceId, Context)]) =>
        foldResults(inputs.map {
          case (source, ctx) => monad.pure[ResultType[PartResult]](Writer(EspExceptionInfo(Some(source.value),
            new IllegalArgumentException(s"Unknown source ${source.value}"), ctx)::Nil, Nil))
        })

      def foldOne(base: ScenarioInternalInterpreterType, nextSource: SourcePart, next: PartInterpreterType[F]) = (inputs: List[(SourceId, Context)]) =>
        foldResults(inputs.map {
          case (source, ctx) if source.value == nextSource.id => next(ctx :: Nil)
          case other => base(other :: Nil)
        })


      compiledProcess.sources.foldLeft[CompilationResult[WithSinkTypes[ScenarioInternalInterpreterType]]](Valid(Writer(Map.empty[String, TypingResult], baseFunction))) {
        case (resultSoFar, e: CustomNodePart) =>
          val compiledTransformer = compileJoinTransformer(e)
          resultSoFar.product(compiledTransformer).map { case (WriterT((types, interpreterMap)), WriterT((types2, part))) =>
            val result = nextPartInvocation(interpreterMap, part)
            Writer[Map[String, TypingResult], ScenarioInternalInterpreterType](types ++ types2, result)
          }
        case (resultSoFar, a: SourcePart) =>
          resultSoFar.product(compiledPartInvoker(a)).map { case (WriterT((types, interpreter)), WriterT((types2, part))) =>
            Writer(types ++ types2, foldOne(interpreter, a, part))
          }
      }
    }

    private def compileWithCompilationErrors(node: SplittedNode[_], validationContext: ValidationContext): ValidatedNel[ProcessCompilationError, Node] =
      processCompilerData.subPartCompiler.compile(node, validationContext)(compiledProcess.metaData).result

    private def lazyParameterInterpreter: CompilerLazyParameterInterpreter = new CompilerLazyParameterInterpreter {
      override def deps: LazyInterpreterDependencies = processCompilerData.lazyInterpreterDeps

      override def metaData: MetaData = processCompilerData.metaData

      override def close(): Unit = {}
    }

    private def compiledPartInvoker(processPart: ProcessPart): CompilationResult[WithSinkTypes[PartInterpreterType[F]]] = processPart match {
      case SourcePart(_, node, validationContext, nextParts, _) =>
        compileWithCompilationErrors(node, validationContext).andThen(partInvoker(_, nextParts))
      case SinkPart(sink, endNode, _, validationContext) =>
        compileWithCompilationErrors(endNode, validationContext).andThen { compiled =>
          partInvoker(compiled, List()).map(prepareResponse(compiled, sink))
        }
      case CustomNodePart(transformerObj, node, _, validationContext, parts, _) =>
        val validatedTransformer = transformerObj match {
          case t: CustomTransformer[F@unchecked] => Valid(t)
          case _ => Invalid(NonEmptyList.of(UnsupportedPart(node.id)))
        }
        validatedTransformer.andThen { transformer =>
          val result = compileWithCompilationErrors(node, validationContext).andThen(partInvoker(_, parts))
          result.map(rs => rs.map(transformer.createTransformation(_, lazyParameterInterpreter)))
        }
    }

    private def prepareResponse(compiledNode: Node, sink: process.Sink)(it: WithSinkTypes[PartInterpreterType[F]]): WithSinkTypes[PartInterpreterType[F]] = sink match {
      case sinkWithParams: BaseEngineSink[Res@unchecked] =>
        implicit val lazyInterpreter: CompilerLazyParameterInterpreter = lazyParameterInterpreter
        val responseLazyParam = sinkWithParams.prepareResponse
        val responseInterpreter = lazyParameterInterpreter.syncInterpretationFunction(responseLazyParam)
        it.bimap(_.updated(compiledNode.id, responseLazyParam.returnType), (originalSink: PartInterpreterType[F]) => (ctxs: List[Context]) => {
          //we invoke 'original sink part' because otherwise listeners wouldn't work properly
          originalSink.apply(ctxs).map { _ =>
            val nodeId = compiledNode.id
            flatten(ctxs.map( ctx =>
              try {
                Writer(Nil, EndResult(nodeId, ctx, responseInterpreter(ctx)) :: Nil): ResultType[PartResult]
              } catch {
                case NonFatal(e) => Writer(EspExceptionInfo(Some(nodeId), e, ctx) :: Nil, Nil): ResultType[PartResult]
              }
            ))
          }
        })

      case other => throw new IllegalArgumentException(s"Not supported sink: $other")
    }

    private def compilePartInvokers(parts: List[SubsequentPart]): CompilationResult[WithSinkTypes[Map[String, PartInterpreterType[F]]]] =
      parts.map(part => compiledPartInvoker(part).map(compiled => part.id -> compiled))
        .sequence[CompilationResult, (String, WithSinkTypes[PartInterpreterType[F]])].map { res =>
        Writer(res.flatMap(_._2.written).toMap, res.toMap.mapValues(_.value))
      }

    private def partInvoker(node: compiledgraph.node.Node, parts: List[SubsequentPart]): CompilationResult[WithSinkTypes[PartInterpreterType[F]]] = {
      compilePartInvokers(parts).map(_.map { partsInvokers =>
        (ctx: List[Context]) => {
          foldResults(ctx.map(invokeInterpreterOnContext(node))).flatMap { results =>
            passingErrors[InterpretationResult, PartResult](results, successful => {
              successful.groupBy(_.reference).map {
                case (pr, ir) => interpretationInvoke(partsInvokers)(pr, ir)
              }.toList.sequence.map(flatten)
            })
          }
        }
      })
    }

    private def invokeInterpreterOnContext(node: Node)(ctx: Context): F[ResultType[InterpretationResult]] = {
      implicit val implicitRunMode: RunMode = runMode
      val interpreterOut = processCompilerData.interpreter.interpret[F](node, processCompilerData.metaData, ctx)
      interpreterOut.map {
        case Left(outputs) => Writer(Nil, outputs)
        case Right(value) => Writer(value :: Nil, Nil)
      }
    }

    private def interpretationInvoke(partInvokers: Map[String, PartInterpreterType[F]])
                                    (pr: PartReference, irs: List[InterpretationResult]): InternalInterpreterOutputType = {
      val results: InternalInterpreterOutputType = pr match {
        case er: EndReference =>
          //FIXME!!!!
          monad.pure[ResultType[PartResult]](Writer(Nil, irs.map(ir => EndResult(er.nodeId, ir.finalContext, ir.output.asInstanceOf[Res]))))
        case _: DeadEndReference =>
          monad.pure[ResultType[PartResult]](Writer(Nil, Nil))
        case r: JoinReference =>
          monad.pure[ResultType[PartResult]](Writer(Nil, irs.map(ir => JoinResult(r, ir.finalContext))))
        case NextPartReference(id) =>
          partInvokers.getOrElse(id, throw new Exception("Unknown reference"))(irs.map(_.finalContext))
      }
      results
    }

    //First we compute scenario parts compiled so far. Then we search for JoinResults and invoke joinPart
    //We know that we'll find all results pointing to join, because we sorted the parts
    private def nextPartInvocation(computedInterpreter: ScenarioInternalInterpreterType,
                                   joinPartToInvoke: List[(String, Context)] => InternalInterpreterOutputType): ScenarioInternalInterpreterType = {
      (ctxs: List[(SourceId, Context)]) => {
        computedInterpreter(ctxs).flatMap { results =>
          passingErrors[PartResult, PartResult](results, successes => {
            val resultsPointingToJoin = successes.collect { case e: JoinResult => (e.reference.branchId, e.context) }
            val endResults: ResultType[PartResult] = Writer(Nil, successes.collect { case e: EndResult[Res@unchecked] => e })
            joinPartToInvoke(resultsPointingToJoin).map(_.add(endResults))
          })
        }
      }
    }

    private def compileJoinTransformer(customNodePart: CustomNodePart): Validated[NonEmptyList[ProcessCompilationError],
      WriterT[Id, Map[String, TypingResult], List[(String, Context)] => InternalInterpreterOutputType]] = {
      val CustomNodePart(transformerObj, node, _, validationContext, parts, _) = customNodePart
      val validatedTransformer = transformerObj match {
        case t: JoinCustomTransformer[F@unchecked] => Valid(t)
        case JoinContextTransformation(_, t: JoinCustomTransformer[F@unchecked]) => Valid(t)
        case _ => Invalid(NonEmptyList.of(UnsupportedPart(node.id)))
      }
      validatedTransformer.andThen { transformer =>
        val result = compileWithCompilationErrors(node, validationContext).andThen(partInvoker(_, parts))
        result.map(rs => rs.map(transformer.createTransformation(_, lazyParameterInterpreter)))
      }
    }

    private def flatten[T](list: List[ResultType[T]]): ResultType[T] =
      list.foldLeft(Writer.value(Nil): ResultType[T])(_.add(_))

    private def foldResults[T](resultsFuture: List[F[ResultType[T]]]): F[ResultType[T]] = resultsFuture.sequence.map(flatten)

    private def passingErrors[In, Out](list: ResultType[In], action: List[In] => F[ResultType[Out]]): F[ResultType[Out]] = {
      WriterT(monad.pure(list.run))
        .flatMap(l => WriterT(action(l).map(_.run)))
        .run.map(k => Writer.apply(k._1, k._2))
    }
  }

}


