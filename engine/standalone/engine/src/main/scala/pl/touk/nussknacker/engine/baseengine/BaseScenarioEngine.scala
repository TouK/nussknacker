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
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, RunMode}
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.baseengine.api.BaseScenarioEngineTypes
import pl.touk.nussknacker.engine.baseengine.api.BaseScenarioEngineTypes.{CustomTransformer, EndResult, GenericListResultType, InternalInterpreterOutputType, InterpretationResultType, InterpreterType, JoinCustomTransformer, JoinResult, PartResultType, SourceId}
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.{RuntimeContext, RuntimeContextLifecycle, RuntimeContextPreparer}
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.compiledgraph.CompiledProcessParts
import pl.touk.nussknacker.engine.compiledgraph.node.{Node, Sink}
import pl.touk.nussknacker.engine.compiledgraph.part._
import pl.touk.nussknacker.engine.definition.{CompilerLazyParameterInterpreter, LazyInterpreterDependencies, ProcessDefinitionExtractor}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.resultcollector.ResultCollector
import pl.touk.nussknacker.engine.split.{NodesCollector, ProcessSplitter}
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.nussknacker.engine.baseengine.metrics.InvocationMetrics
import pl.touk.nussknacker.engine.standalone.api.{StandaloneSink, StandaloneSource}
import pl.touk.nussknacker.engine.{ModelData, compiledgraph}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds
import scala.util.control.NonFatal

trait BaseScenarioEngine[F[_], Res] {

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

  private def extractSource(compiledProcess: CompiledProcessParts): List[SourcePart] = compiledProcess.sources.collect {
    case a: SourcePart => a
  }

  case class ScenarioInterpreter(sources: List[SourcePart],
                                 sinkTypes: Map[String, TypingResult],
                                 context: RuntimeContext,
                                 private val invoker: List[(SourceId, Context)] => InternalInterpreterOutputType[F],
                                 private val lifecycle: Seq[Lifecycle],
                                 private val modelData: ModelData
                                )(implicit monad: MonadError[F, Throwable]) extends InvocationMetrics with AutoCloseable {

    val id: String = context.processId

    def invoke(contexts: List[(SourceId, Context)]): F[InterpretationResultType[Res]] = modelData.withThisAsContextClassLoader {
      invoker(contexts).map { result =>
        result.right.map(_.map {
          case e:EndResult[Res@unchecked] => e
          case other => throw new IllegalArgumentException(s"Should not happen, $other left in results")
        })
      }
    }

  private def extractSource(compiledProcess: CompiledProcessParts): StandaloneSource[Any] =
    compiledProcess.sources.head.asInstanceOf[SourcePart].obj.asInstanceOf[StandaloneSource[Any]]


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

    type WithSinkTypes[K] = Writer[Map[String, TypingResult], K]

    type ScenarioInternalInterpreterType = List[(SourceId, Context)] => InternalInterpreterOutputType[F]

    def compile: CompilationResult[WithSinkTypes[ScenarioInternalInterpreterType]] = {
      //here we rely on the fact that parts are sorted correctly (see ProcessCompiler.compileSources)
      //this guarantess that SourcePart is first
      //val NonEmptyList(start, rest) = compiledProcess.sources

      val baseFunction: ScenarioInternalInterpreterType = (inputs: List[(SourceId, Context)]) =>
        foldResults(inputs.map {
          case (source, ctx) => monad.pure[GenericListResultType[PartResultType]](Left(NonEmptyList.one(EspExceptionInfo(Some(source.value),
            new IllegalArgumentException(s"Unknown source ${source.value}"), ctx))))
        })

      def foldOne(base: ScenarioInternalInterpreterType, nextSource: SourcePart, next: InterpreterType[F]) = (inputs: List[(SourceId, Context)]) =>
        foldResults(inputs.map {
          case (source, ctx) if source.value == nextSource.id => next(ctx :: Nil)
          case other => base(other :: Nil)
        })


      compiledProcess.sources.foldLeft[CompilationResult[WithSinkTypes[ScenarioInternalInterpreterType]]](Valid(Writer(Map.empty[String, TypingResult], baseFunction))) {
        case (resultSoFar, e: CustomNodePart) =>
          val compiledTransformer = compileJoinTransformer(e)
          resultSoFar.product(compiledTransformer).map { case (WriterT((types, interpreterMap)), WriterT((types2, part))) =>
            Writer(types ++ types2, nextPartInvocation(interpreterMap, part))
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

    private def compiledPartInvoker(processPart: ProcessPart): CompilationResult[WithSinkTypes[InterpreterType[F]]] = processPart match {
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
          result.map(rs => rs.map(transformer.createTransformation(node.data.outputVar)(_, lazyParameterInterpreter)))
        }
    }

    private def prepareResponse(compiledNode: Node, sink: process.Sink)(it: WithSinkTypes[InterpreterType[F]]): WithSinkTypes[InterpreterType[F]] = sink match {
      case sinkWithParams: StandaloneSink =>
        implicit val lazyInterpreter: CompilerLazyParameterInterpreter = lazyParameterInterpreter
        val responseLazyParam = sinkWithParams.prepareResponse
        val responseInterpreter = lazyParameterInterpreter.createInterpreter(responseLazyParam)
        it.bimap(_.updated(compiledNode.id, responseLazyParam.returnType), (originalSink: InterpreterType[F]) => (ctxs: List[Context]) => {
          //we invoke 'original sink part' because otherwise listeners wouldn't work properly
          originalSink.apply(ctxs).flatMap { _ =>
            flatten(ctx => shape.fromFuture(ec)(responseInterpreter(ec, ctx)).map(res => {
              //FIXME!!!!
              Right(List(EndResult(compiledNode.id, ctx, res.asInstanceOf[Res])))
            }))(ctxs)
          }.recover {
            case NonFatal(e) =>
              Left(NonEmptyList.of(EspExceptionInfo(Some(compiledNode.id), e, ctxs.head)))
          }
        })
      case other => throw new IllegalArgumentException(s"Not supported sink: $other")
    }

    private def compilePartInvokers(parts: List[SubsequentPart]): CompilationResult[WithSinkTypes[Map[String, InterpreterType[F]]]] =
      parts.map(part => compiledPartInvoker(part).map(compiled => part.id -> compiled))
        .sequence[CompilationResult, (String, WithSinkTypes[InterpreterType[F]])].map { res =>
        Writer(res.flatMap(_._2.written).toMap, res.toMap.mapValues(_.value))
      }

    private def partInvoker(node: compiledgraph.node.Node, parts: List[SubsequentPart]): CompilationResult[WithSinkTypes[InterpreterType[F]]] = {
      compilePartInvokers(parts).map(_.map { partsInvokers =>
        (ctx: List[Context]) => {
          (for {
            resultList <- EitherT(foldResults(ctx.map(invokeInterpreterOnContext(node))))
            //we group results, to invoke specific part only once, with complete list of results
            groupedResults <- EitherT(foldResults(resultList.groupBy(_.reference).map {
              case (pr, ir) => interpretationInvoke(partsInvokers)(pr, ir)
            }.toList))
          } yield groupedResults).value
        }
      })
    }

    private def invokeInterpreterOnContext(node: Node)(ctx: Context) = {
      implicit val implicitRunMode: RunMode = runMode
      processCompilerData.interpreter.interpret[F](node, processCompilerData.metaData, ctx).map(_.swap.leftMap(NonEmptyList.one))
    }

    private def interpretationInvoke(partInvokers: Map[String, InterpreterType[F]])
                                    (pr: PartReference, irs: List[InterpretationResult]): InternalInterpreterOutputType[F] = {
      val results: InternalInterpreterOutputType[F] = pr match {
        case er: EndReference =>
          //FIXME!!!!
          monad.pure(Right(irs.map(ir => EndResult(er.nodeId, ir.finalContext, ir.output.asInstanceOf[Res]))))
        case _: DeadEndReference =>
          monad.pure(Right(Nil))
        case r: JoinReference =>
          monad.pure(Right(irs.map(ir => JoinResult(r, ir.finalContext))))
        case NextPartReference(id) =>
          partInvokers.getOrElse(id, throw new Exception("Unknown reference"))(irs.map(_.finalContext))
      }
      results
    }

    //First we compute scenario parts compiled so far. Then we search for JoinResults and invoke joinPart
    //We know that we'll find all results pointing to join, because we sorted the parts
    private def nextPartInvocation(computedInterpreter: ScenarioInternalInterpreterType,
                                   joinPartToInvoke: Map[String, List[Context]] => InternalInterpreterOutputType[F]) = {
      (ctxs: List[(SourceId, Context)]) => {
        (for {
          results <- EitherT(computedInterpreter(ctxs))
          allResults <- EitherT({
            val resultsPointingToJoin = results.collect { case e: JoinResult => e }.groupBy(_.reference.branchId).mapValues(_.map(_.context))
            val endResults = results.collect { case e: EndResult[Res@unchecked] => e }
            joinPartToInvoke(resultsPointingToJoin).map(_.map(_ ++ endResults))
          })
        } yield allResults).value
      }
    }

    private def compileJoinTransformer(customNodePart: CustomNodePart): Validated[NonEmptyList[ProcessCompilationError], WriterT[Id, Map[String, TypingResult], Map[String, List[Context]] => InternalInterpreterOutputType[F]]] = {
      val CustomNodePart(transformerObj, node, _, validationContext, parts, _) = customNodePart
      val validatedTransformer = transformerObj match {
        case t: JoinCustomTransformer[F@unchecked] => Valid(t)
        case JoinContextTransformation(_, t: JoinCustomTransformer[F@unchecked]) => Valid(t)
        case _ => Invalid(NonEmptyList.of(UnsupportedPart(node.id)))
      }
      validatedTransformer.andThen { transformer =>
        val result = compileWithCompilationErrors(node, validationContext).andThen(partInvoker(_, parts))
        result.map(rs => rs.map(transformer.createTransformation(node.data.outputVar)(_, lazyParameterInterpreter)))
      }
    }

    private def foldResults[T, Error](resultsFuture: List[F[GenericListResultType[T]]]): F[GenericListResultType[T]] = {
      //Validated would be better here?
      resultsFuture.map(EitherT(_)).sequence.map(_.flatten).value
    }

    private def flatten(fun: Context => InternalInterpreterOutputType[F]): InterpreterType[F] = ctxs => foldResults(ctxs.map(fun))

  }

}


