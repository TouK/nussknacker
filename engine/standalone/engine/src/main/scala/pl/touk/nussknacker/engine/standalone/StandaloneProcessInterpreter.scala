package pl.touk.nussknacker.engine.standalone

import java.util.concurrent.atomic.AtomicLong

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel, Writer}
import pl.touk.nussknacker.engine.api.async.DefaultAsyncInterpretationValueDeterminer
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.UnsupportedPart
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, EspExceptionInfo}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{process, _}
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.compiledgraph.node.{Node, Sink}
import pl.touk.nussknacker.engine.compiledgraph.part._
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.{CompilerLazyParameterInterpreter, LazyInterpreterDependencies, ProcessDefinitionExtractor}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.nussknacker.engine.standalone.api.types._
import pl.touk.nussknacker.engine.standalone.api.{StandaloneCustomTransformer, StandaloneSource, types}
import pl.touk.nussknacker.engine.standalone.metrics.InvocationMetrics
import pl.touk.nussknacker.engine.standalone.utils.{StandaloneContext, StandaloneContextLifecycle, StandaloneContextPreparer, StandaloneSinkWithParameters}
import pl.touk.nussknacker.engine.{Interpreter, ModelData, compiledgraph}

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
            additionalListeners: List[ProcessListener] = List(),
            definitionsPostProcessor: ProcessDefinitionExtractor.ProcessDefinition[ObjectWithMethodDef]
              => ProcessDefinitionExtractor.ProcessDefinition[ObjectWithMethodDef] = identity)
  : ValidatedNel[ProcessCompilationError, StandaloneProcessInterpreter] = modelData.withThisAsContextClassLoader {

    val creator = modelData.configCreator
    val processObjectDependencies = ProcessObjectDependencies(modelData.processConfig, modelData.objectNaming)

    val extractedDefinitions = ProcessDefinitionExtractor.extractObjectWithMethods(creator, processObjectDependencies)
    val definitions = definitionsPostProcessor(extractedDefinitions)
    val listeners = creator.listeners(processObjectDependencies) ++ additionalListeners

    CompiledProcess.compile(process,
      definitions,
      listeners,
      modelData.modelClassLoader.classLoader
      // defaultAsyncValue is not important here because it doesn't used in standalone mode
    )(DefaultAsyncInterpretationValueDeterminer.DefaultValue).andThen { compiledProcess =>
      val source = extractSource(compiledProcess)
      StandaloneInvokerCompiler(compiledProcess).compile.map(_.run).map { case (sinkTypes, invoker) =>
        StandaloneProcessInterpreter(source, sinkTypes, contextPreparer.prepare(process.id), invoker, compiledProcess.lifecycle, modelData)
      }
    }
  }

  private def extractSource(compiledProcess: CompiledProcess): StandaloneSource[Any]
    = compiledProcess.parts.sources.head.asInstanceOf[SourcePart].obj.asInstanceOf[StandaloneSource[Any]]


  private case class StandaloneInvokerCompiler(compiledProcess: CompiledProcess) {

    import cats.implicits._
    type CompilationResult[K] = ValidatedNel[ProcessCompilationError, K]

    type WithSinkTypes[K] = Writer[Map[String, TypingResult], K]

    private def compileWithCompilationErrors(node: SplittedNode[_], validationContext: ValidationContext): ValidatedNel[ProcessCompilationError, Node] =
      compiledProcess.subPartCompiler.compile(node, validationContext).result

    private def lazyParameterInterpreter: CompilerLazyParameterInterpreter = new CompilerLazyParameterInterpreter {
      override def deps: LazyInterpreterDependencies = compiledProcess.lazyInterpreterDeps

      override def metaData: MetaData = compiledProcess.parts.metaData

      override def close(): Unit = {}

      override def exceptionHandler: EspExceptionHandler = compiledProcess.parts.exceptionHandler
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
          result.map(rs => rs.map(transformer.createTransformation(node.data.outputVar.get)(_, lazyParameterInterpreter)))
        }
    }

    private def prepareResponse(compiledNode: Node, sink:process.Sink)(it: WithSinkTypes[InterpreterType]): WithSinkTypes[InterpreterType] = sink match {
      case sinkWithParams:StandaloneSinkWithParameters =>
        implicit val lazyInterpreter: CompilerLazyParameterInterpreter = lazyParameterInterpreter
        val responseLazyParam = sinkWithParams.prepareResponse
        val responseInterpreter = lazyParameterInterpreter.createInterpreter(responseLazyParam)
        it.bimap(_.updated(compiledNode.id, responseLazyParam.returnType), (originalSink:InterpreterType) => (ctx: Context, ec:ExecutionContext) => {
          //we ignore result of original Sink (we currently don't support output in StandaloneSinkWithParameters)
          //but we invoke it because otherwise listeners wouldn't work properly
          originalSink.apply(ctx, ec).flatMap { _ =>
            responseInterpreter(ec, ctx).map(res => Right(List(InterpretationResult(EndReference(compiledNode.id), res, ctx))))(ec)
          }(ec).recover{
            case NonFatal(e) =>
              Left(NonEmptyList.of(EspExceptionInfo(Some(compiledNode.id), e, ctx)))
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
        (ctx: Context, ec: ExecutionContext) => {
          implicit val iec: ExecutionContext = ec
          compiledProcess.interpreter.interpret(node, compiledProcess.parts.metaData, ctx).flatMap { maybeResult =>
            maybeResult.fold[InterpreterOutputType](
            ir => Future.sequence(ir.map(interpretationInvoke(partsInvokers))).map(foldResults),
            a => Future.successful(Left(NonEmptyList.of(a))))
          }
        }
      })
    }

    private def interpretationInvoke(partInvokers: Map[String, InterpreterType])(ir: InterpretationResult)(implicit ec: ExecutionContext) = {
      val results = ir.reference match {
        case _: EndReference =>
          Future.successful(Right(List(ir)))
        case _: DeadEndReference =>
          Future.successful(Right(Nil))
        case _: JoinReference =>
          Future.successful(Right(Nil))
        case NextPartReference(id) =>
          partInvokers.getOrElse(id, throw new Exception("Unknown reference"))(ir.finalContext, ec)
      }
      results
    }

    def compile: CompilationResult[WithSinkTypes[InterpreterType]]
      = compiledPartInvoker(compiledProcess.parts.sources.head)

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
      val ctx = Context(contextId).withVariable(Interpreter.InputParamName, input)
      invoker(ctx, ec)
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
