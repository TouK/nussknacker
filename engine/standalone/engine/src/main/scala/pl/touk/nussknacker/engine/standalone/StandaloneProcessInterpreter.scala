package pl.touk.nussknacker.engine.standalone

import java.util.concurrent.atomic.AtomicLong
import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel, Writer, WriterT}
import pl.touk.nussknacker.engine.Interpreter.{FutureShape, InterpreterShape}
import pl.touk.nussknacker.engine.api.async.DefaultAsyncInterpretationValueDeterminer
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, UnsupportedPart}
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, JoinContextTransformation, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{process, _}
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.compiledgraph.CompiledProcessParts
import pl.touk.nussknacker.engine.compiledgraph.node.{Node, Sink}
import pl.touk.nussknacker.engine.compiledgraph.part._
import pl.touk.nussknacker.engine.definition.{CompilerLazyParameterInterpreter, LazyInterpreterDependencies, ProcessDefinitionExtractor}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.resultcollector.ResultCollector
import pl.touk.nussknacker.engine.split.{NodesCollector, ProcessSplitter}
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.nussknacker.engine.standalone.api.types._
import pl.touk.nussknacker.engine.standalone.api.{JoinStandaloneCustomTransformer, StandaloneCustomTransformer, StandaloneSource, types}
import pl.touk.nussknacker.engine.standalone.metrics.InvocationMetrics
import pl.touk.nussknacker.engine.standalone.utils.{StandaloneContext, StandaloneContextLifecycle, StandaloneContextPreparer, StandaloneSinkWithParameters}
import pl.touk.nussknacker.engine.{ModelData, compiledgraph}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

object StandaloneProcessInterpreter {


  def foldResults[T, Error](results: List[GenericListResultType[T]]): GenericListResultType[T] = {
    //Validated would be better here?
    //TODO: can we replace it with sequenceU??
    results.foldLeft[GenericListResultType[T]](Right(Nil)) {
      case (Right(a), Right(b)) => Right(a ++ b)
      case (Left(a), Right(_)) => Left(a)
      case (Right(_), Left(a)) => Left(a)
      case (Left(a), Left(b)) => Left(a ++ b.toList)
    }
  }

  def apply(process: EspProcess, contextPreparer: StandaloneContextPreparer, modelData: ModelData,
            additionalListeners: List[ProcessListener], resultCollector: ResultCollector)
  : ValidatedNel[ProcessCompilationError, StandaloneProcessInterpreter] = modelData.withThisAsContextClassLoader {

    val creator = modelData.configCreator
    val processObjectDependencies = ProcessObjectDependencies(modelData.processConfig, modelData.objectNaming)

    val definitions = ProcessDefinitionExtractor.extractObjectWithMethods(creator, processObjectDependencies)
    val listeners = creator.listeners(processObjectDependencies) ++ additionalListeners

    val compilerData = ProcessCompilerData.prepare(process,
      definitions,
      listeners,
      modelData.modelClassLoader.classLoader, resultCollector
      // defaultAsyncValue is not important here because it isn't used in standalone mode
    )(DefaultAsyncInterpretationValueDeterminer.DefaultValue)

    compilerData.compile().andThen { compiledProcess =>
      val source = extractSource(compiledProcess)

      val nodesUsed = NodesCollector.collectNodesInAllParts(ProcessSplitter.split(process).sources).map(_.data)
      val lifecycle = compilerData.lifecycle(nodesUsed)
      StandaloneInvokerCompiler(compiledProcess, compilerData).compile.map(_.run).map { case (sinkTypes, invoker) =>
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

    private def compileWithCompilationErrors(node: SplittedNode[_], validationContext: ValidationContext): ValidatedNel[ProcessCompilationError, Node] =
      processCompilerData.subPartCompiler.compile(node, validationContext)(compiledProcess.metaData).result

    private def lazyParameterInterpreter: CompilerLazyParameterInterpreter = new CompilerLazyParameterInterpreter {
      override def deps: LazyInterpreterDependencies = processCompilerData.lazyInterpreterDeps

      override def metaData: MetaData = processCompilerData.metaData

      override def close(): Unit = {}
    }

    private def compiledPartInvoker(processPart: ProcessPart): CompilationResult[WithSinkTypes[InterpreterType]] = processPart match {
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


    private def compilePartInvokers(parts: List[SubsequentPart]) : CompilationResult[WithSinkTypes[Map[String, InterpreterType]]] =
      parts.map(part => compiledPartInvoker(part).map(compiled => part.id -> compiled))
        .sequence[CompilationResult, (String, WithSinkTypes[InterpreterType])].map { res =>
          Writer(res.flatMap(_._2.written).toMap, res.toMap.mapValues(_.value))
      }

    private def partInvoker(node: compiledgraph.node.Node, parts: List[SubsequentPart]): CompilationResult[WithSinkTypes[InterpreterType]] = {

      compilePartInvokers(parts).map(_.map { partsInvokers =>
        (ctx: List[Context], ec: ExecutionContext) => {
          implicit val iec: ExecutionContext = ec
          //TODO: refactor StandaloneInterpreter to use IO
          implicit val shape: InterpreterShape[Future] = new FutureShape
          Future.sequence(ctx.map { one =>
            processCompilerData.interpreter.interpret[Future](node, processCompilerData.metaData, one)
          }).flatMap { list =>
            val foldedResults = foldResults(list.map(_.swap.leftMap(NonEmptyList.one)))
            val interpreterd = foldedResults.map { resultList =>
              resultList.groupBy(_.reference).map {
                case (pr, ir) => interpretationInvoke(partsInvokers)(pr, ir)
              }.toList
            }
            interpreterd match {
              case Right(other) => Future.sequence(other).map(foldResults)
              case Left(err) => Future.successful(Left(err))
            }
          }
        }
      })
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

    def compile: CompilationResult[WithSinkTypes[InterpreterType]] = {
      //here we rely on the fact that parts are sorted correctly (see ProcessCompiler.compileSources)
      //this guarantess that SourcePart is first
      val NonEmptyList(start, rest) = compiledProcess.sources
      rest.foldLeft(compiledPartInvoker(start)) {
        case (resultSoFar, CustomNodePart(transformerObj, node, _, validationContext, parts, _)) =>
          val validatedTransformer = transformerObj match {
            case t: JoinStandaloneCustomTransformer => Valid(t)
            case JoinContextTransformation(_, t: JoinStandaloneCustomTransformer) => Valid(t)
            case _ => Invalid(NonEmptyList.of(UnsupportedPart(node.id)))
          }
          val compiled = validatedTransformer.andThen { transformer =>
            val result = compileWithCompilationErrors(node, validationContext).andThen(partInvoker(_, parts))
            result.map(rs => rs.map(transformer.createTransformation(node.data.outputVar)(_, lazyParameterInterpreter)))
          }
          resultSoFar.product(compiled).map { case (WriterT((types, interpreter)), WriterT((types2, part))) =>
            Writer(types ++ types2, (ctxs: List[Context], ec: ExecutionContext) => {
              implicit val vec: ExecutionContext = ec
              interpreter(ctxs, ec).flatMap {
                case Right(results) =>
                  val nodes = results.collect { case e:JoinResult => e }.groupBy(_.reference.id).mapValues(_.map(_.context))
                  val realResults = results.collect { case e:EndResult => e }
                  part(nodes, ec).map(_.map(_ ++ realResults))
                case errors => Future.successful(errors)
              }
            })
          }
        //thanks to sorting we know that one SourcePart was first on parts list
        case (_, a:SourcePart) => Invalid(NonEmptyList.one(CustomNodeError(a.id, "This type of scenario can have only one source", None)))
      }
    }

    private def flatten(fun: (Context, ExecutionContext) => InternalInterpreterOutputType): InterpreterType = {
      (ctxs, ec) => {
        implicit val impl: ExecutionContext = ec
        Future.sequence(ctxs.map(fun(_, ec))).map { resList =>
          foldResults(resList)
        }
      }
    }
  }

}




case class StandaloneProcessInterpreter(source: StandaloneSource[Any],
                                        sinkTypes: Map[String, TypingResult],
                                        context: StandaloneContext,
                                        private val invoker: types.InterpreterType,
                                        private val lifecycle: Seq[Lifecycle],
                                        private val modelData: ModelData) extends InvocationMetrics {

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
