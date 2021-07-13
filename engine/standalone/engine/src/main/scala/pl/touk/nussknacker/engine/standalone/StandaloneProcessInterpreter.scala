package pl.touk.nussknacker.engine.standalone

import cats.Id
import java.util.concurrent.atomic.AtomicLong

import cats.data.Validated.{Invalid, Valid}
import cats.data.{EitherT, NonEmptyList, Validated, ValidatedNel, Writer, WriterT}
import pl.touk.nussknacker.engine.Interpreter.{FutureShape, InterpreterShape}
import pl.touk.nussknacker.engine.api.async.DefaultAsyncInterpretationValueDeterminer
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, UnsupportedPart}
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, RunMode}
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{process, _}
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.compiledgraph.CompiledProcessParts
import pl.touk.nussknacker.engine.compiledgraph.node.{Node, Sink}
import pl.touk.nussknacker.engine.compiledgraph.part.{CustomNodePart, _}
import pl.touk.nussknacker.engine.definition.{CompilerLazyParameterInterpreter, LazyInterpreterDependencies, ProcessDefinitionExtractor}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.resultcollector.ResultCollector
import pl.touk.nussknacker.engine.split.{NodesCollector, ProcessSplitter}
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.nussknacker.engine.standalone.api.types._
import pl.touk.nussknacker.engine.standalone.api._
import pl.touk.nussknacker.engine.standalone.metrics.InvocationMetrics
import pl.touk.nussknacker.engine.{ModelData, compiledgraph}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Using.Releasable
import scala.util.control.NonFatal

object StandaloneProcessInterpreter {

  def apply(process: EspProcess, contextPreparer: StandaloneContextPreparer, modelData: ModelData,
            additionalListeners: List[ProcessListener], resultCollector: ResultCollector, runMode: RunMode)
  : ValidatedNel[ProcessCompilationError, StandaloneProcessInterpreter] = modelData.withThisAsContextClassLoader {

    val creator = modelData.configCreator
    val processObjectDependencies = ProcessObjectDependencies(modelData.processConfig, modelData.objectNaming)

    val definitions = ProcessDefinitionExtractor.extractObjectWithMethods(creator, processObjectDependencies)
    val listeners = creator.listeners(processObjectDependencies) ++ additionalListeners

    val compilerData = ProcessCompilerData.prepare(process,
      definitions,
      listeners,
      modelData.modelClassLoader.classLoader, resultCollector,
      // defaultAsyncValue is not important here because it isn't used in standalone mode
    )(DefaultAsyncInterpretationValueDeterminer.DefaultValue)

    compilerData.compile()(runMode).andThen { compiledProcess =>
      val source = extractSource(compiledProcess)

      val nodesUsed = NodesCollector.collectNodesInAllParts(ProcessSplitter.split(process).sources).map(_.data)
      val lifecycle = compilerData.lifecycle(nodesUsed)
      StandaloneInvokerCompiler(compiledProcess, compilerData).compile(runMode).map(_.run).map { case (sinkTypes, invoker) =>
        StandaloneProcessInterpreter(source, sinkTypes, contextPreparer.prepare(process.id), invoker, lifecycle, modelData)
      }
    }
  }

  private def extractSource(compiledProcess: CompiledProcessParts): StandaloneSource[Any]
    = compiledProcess.sources.head.asInstanceOf[SourcePart].obj.asInstanceOf[StandaloneSource[Any]]


  private case class StandaloneInvokerCompiler(compiledProcess: CompiledProcessParts, processCompilerData: ProcessCompilerData) {

    import cats.implicits._
    type CompilationResult[K] = ValidatedNel[ProcessCompilationError, K]

    type WithSinkTypes[K] = Writer[Map[String, TypingResult], K]

    private def compileWithCompilationErrors(node: SplittedNode[_], validationContext: ValidationContext)
                                            (implicit runMode: RunMode): ValidatedNel[ProcessCompilationError, Node] =
      processCompilerData.subPartCompiler.compile(node, validationContext)(compiledProcess.metaData, runMode).result

    private def lazyParameterInterpreter: CompilerLazyParameterInterpreter = new CompilerLazyParameterInterpreter {
      override def deps: LazyInterpreterDependencies = processCompilerData.lazyInterpreterDeps

      override def metaData: MetaData = processCompilerData.metaData

      override def close(): Unit = {}
    }

    private def compiledPartInvoker(processPart: ProcessPart)
                                   (implicit runMode: RunMode): CompilationResult[WithSinkTypes[InterpreterType]] = processPart match {
      case SourcePart(_, node, validationContext, nextParts, _) =>
        compileWithCompilationErrors(node, validationContext).andThen(partInvoker(_, nextParts))
      case SinkPart(sink, endNode, _, validationContext) =>
        compileWithCompilationErrors(endNode, validationContext).andThen { compiled =>
          partInvoker(compiled, List()).map(prepareResponse(compiled, sink))
        }
      case CustomNodePart(transformerObj, node, _, validationContext, parts, _) =>
        val validatedTransformer = transformerObj match {
          case t: StandaloneCustomTransformer => Valid(t)
          case ContextTransformation(_, t: StandaloneCustomTransformer) => Valid(t)
          case _ => Invalid(NonEmptyList.of(UnsupportedPart(node.id)))
        }
        validatedTransformer.andThen { transformer =>
          val result = compileWithCompilationErrors(node, validationContext).andThen(partInvoker(_, parts))
          result.map(rs => rs.map(transformer.createTransformation(node.data.outputVar)(_, lazyParameterInterpreter)))
        }
    }

    private def prepareResponse(compiledNode: Node, sink:process.Sink)(it: WithSinkTypes[InterpreterType]): WithSinkTypes[InterpreterType] = sink match {
      case sinkWithParams:StandaloneSinkWithParameters =>
        implicit val lazyInterpreter: CompilerLazyParameterInterpreter = lazyParameterInterpreter
        val responseLazyParam = sinkWithParams.prepareResponse
        val responseInterpreter = lazyParameterInterpreter.createInterpreter(responseLazyParam)
        it.bimap(_.updated(compiledNode.id, responseLazyParam.returnType), (originalSink:InterpreterType) => (ctxs: List[Context], ec:ExecutionContext) => {
          //we ignore result of original Sink (we currently don't support output in StandaloneSinkWithParameters)
          //but we invoke it because otherwise listeners wouldn't work properly
          originalSink.apply(ctxs, ec).flatMap { _ =>
            flatten((ctx, ec) => responseInterpreter(ec, ctx).map(res => {
              Right(List(EndResult(InterpretationResult(EndReference(compiledNode.id), res, ctx)))) } )(ec))(ctxs, ec)
          }(ec).recover{
            case NonFatal(e) =>
              Left(NonEmptyList.of(EspExceptionInfo(Some(compiledNode.id), e, ctxs.head)))
          }(ec)
        })
      case _ => it.mapWritten(_.updated(compiledNode.id, compiledNode.asInstanceOf[Sink].endResult.map(_._2).getOrElse(Unknown)))
    }


    private def compilePartInvokers(parts: List[SubsequentPart])
                                   (implicit runMode: RunMode): CompilationResult[WithSinkTypes[Map[String, InterpreterType]]] =
      parts.map(part => compiledPartInvoker(part).map(compiled => part.id -> compiled))
        .sequence[CompilationResult, (String, WithSinkTypes[InterpreterType])].map { res =>
          Writer(res.flatMap(_._2.written).toMap, res.toMap.mapValues(_.value))
      }

    private def partInvoker(node: compiledgraph.node.Node, parts: List[SubsequentPart])
                           (implicit runMode: RunMode): CompilationResult[WithSinkTypes[InterpreterType]] = {
      compilePartInvokers(parts).map(_.map { partsInvokers =>
        (ctx: List[Context], ec: ExecutionContext) => {
          implicit val iec: ExecutionContext = ec
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

    private def invokeInterpreterOnContext(node: Node)(ctx: Context)
                                          (implicit ec: ExecutionContext, runMode: RunMode) = {
      //TODO: refactor StandaloneInterpreter to use IO
      implicit val shape: InterpreterShape[Future] = new FutureShape
      processCompilerData.interpreter.interpret[Future](node, processCompilerData.metaData, ctx).map(_.swap.leftMap(NonEmptyList.one))
    }

    private def interpretationInvoke(partInvokers: Map[String, InterpreterType])
                                    (pr: PartReference, ir: List[InterpretationResult])(implicit ec: ExecutionContext): InternalInterpreterOutputType = {
      val results = pr match {
        case _: EndReference =>
          Future.successful(Right(ir.map(EndResult)))
        case _: DeadEndReference =>
          Future.successful(Right(Nil))
        case r: JoinReference =>
          Future.successful(Right(ir.map(one => JoinResult(r, one.finalContext))))
        case NextPartReference(id) =>
          partInvokers.getOrElse(id, throw new Exception("Unknown reference"))(ir.map(_.finalContext), ec)
      }
      results
    }

    def compile(implicit runMode: RunMode): CompilationResult[WithSinkTypes[InterpreterType]] = {
      //here we rely on the fact that parts are sorted correctly (see ProcessCompiler.compileSources)
      //this guarantess that SourcePart is first
      val NonEmptyList(start, rest) = compiledProcess.sources
      rest.foldLeft(compiledPartInvoker(start)) {
        case (resultSoFar, e:CustomNodePart) =>
          val compiledTransformer = compileJoinTransformer(e)
          resultSoFar.product(compiledTransformer).map { case (WriterT((types, interpreter)), WriterT((types2, part))) =>
            Writer(types ++ types2, nextPartInvocation(interpreter, part))
          }
        //thanks to sorting we know that one SourcePart was first on parts list
        //Currently we do not allow > 1 Source for standalone
        case (_, a:SourcePart) => Invalid(NonEmptyList.one(CustomNodeError(a.id, "This type of scenario can have only one source", None)))
      }
    }

    //First we compute scenario parts compiled so far. Then we search for JoinResults and invoke joinPart
    //We know that we'll find all results pointing to join, because we sorted the parts
    private def nextPartInvocation(computedInterpreter: InterpreterType,
                               joinPartToInvoke: (Map[String, List[Context]], ExecutionContext) => InternalInterpreterOutputType) = {
      (ctxs: List[Context], ec: ExecutionContext) => {
        implicit val vec: ExecutionContext = ec
        (for {
          results <- EitherT(computedInterpreter(ctxs, ec))
          allResults <- EitherT({
            val resultsPointingToJoin = results.collect { case e:JoinResult => e }.groupBy(_.reference.branchId).mapValues(_.map(_.context))
            val endResults = results.collect { case e:EndResult => e }
            joinPartToInvoke(resultsPointingToJoin, ec).map(_.map(_ ++ endResults))
          })
        } yield allResults).value
      }
    }

    private def compileJoinTransformer(customNodePart: CustomNodePart)
                                      (implicit runMode: RunMode): Validated[NonEmptyList[ProcessCompilationError], WriterT[Id, Map[String, TypingResult], (Map[String, List[Context]], ExecutionContext) => InternalInterpreterOutputType]] = {
      val CustomNodePart(transformerObj, node, _, validationContext, parts, _) = customNodePart
      val validatedTransformer = transformerObj match {
        case t: JoinStandaloneCustomTransformer => Valid(t)
        case JoinContextTransformation(_, t: JoinStandaloneCustomTransformer) => Valid(t)
        case _ => Invalid(NonEmptyList.of(UnsupportedPart(node.id)))
      }
      validatedTransformer.andThen { transformer =>
        val result = compileWithCompilationErrors(node, validationContext).andThen(partInvoker(_, parts))
        result.map(rs => rs.map(transformer.createTransformation(node.data.outputVar)(_, lazyParameterInterpreter)))
      }
    }

    private def foldResults[T, Error](resultsFuture: List[Future[GenericListResultType[T]]])
                             (implicit ec: ExecutionContext): Future[GenericListResultType[T]] = {
      //Validated would be better here?
      resultsFuture.map(EitherT(_)).sequence.map(_.flatten).value
    }

    private def flatten(fun: (Context, ExecutionContext) => InternalInterpreterOutputType): InterpreterType = {
      (ctxs, ec) => foldResults(ctxs.map(fun(_, ec)))(ec)
    }
  }

}

case class StandaloneProcessInterpreter(source: StandaloneSource[Any],
                                        sinkTypes: Map[String, TypingResult],
                                        context: StandaloneContext,
                                        private val invoker: types.InterpreterType,
                                        private val lifecycle: Seq[Lifecycle],
                                        private val modelData: ModelData) extends InvocationMetrics with AutoCloseable {

  val id: String = context.processId

  private val counter = new AtomicLong(0)

  def invoke(input: Any, contextIdOpt: Option[String] = None)(implicit ec: ExecutionContext): Future[GenericListResultType[Any]] = {
    invokeToResult(input, contextIdOpt).map(_.right.map(_.map(_.output)))
  }

  def invokeToResult(input: Any, contextIdOpt: Option[String] = None)(implicit ec: ExecutionContext): InterpreterOutputType = modelData.withThisAsContextClassLoader {
    val contextId = contextIdOpt.getOrElse(s"${context.processId}-${counter.getAndIncrement()}")
    measureTime {
      val ctx = Context(contextId).withVariable(VariableConstants.InputVariableName, input)
      invoker(ctx:: Nil, ec).map { result =>
        result.right.map(_.map {
          case EndResult(result) => result
          case other => throw new IllegalArgumentException(s"Should not happen, $other left in results")
        })

      }
    }
  }

  def open(jobData: JobData): Unit = modelData.withThisAsContextClassLoader {
    lifecycle.foreach {
      case a:StandaloneContextLifecycle => a.open(jobData, context)
      case a => a.open(jobData)
    }
  }

  def close(): Unit = modelData.withThisAsContextClassLoader {
    lifecycle.foreach(_.close())
    context.close()
  }

}
