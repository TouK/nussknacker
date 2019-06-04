package pl.touk.nussknacker.engine.standalone

import java.util.concurrent.atomic.AtomicLong

import cats.data
import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.UnsupportedPart
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.compile._
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
    val config = modelData.processConfig

    val definitions = definitionsPostProcessor(ProcessDefinitionExtractor.extractObjectWithMethods(creator, config))
    val listeners = creator.listeners(config) ++ additionalListeners

    CompiledProcess.compile(process,
      definitions,
      listeners,
      //FIXME: timeout
      modelData.modelClassLoader.classLoader
    ).andThen { compiledProcess =>
      val source = compiledProcess.parts.sources.head.asInstanceOf[SourcePart].obj.asInstanceOf[StandaloneSource[Any]]
      StandaloneInvokerCompiler(compiledProcess).compile.map { invoker =>
        StandaloneProcessInterpreter(contextPreparer.prepare(process.id), source, invoker, compiledProcess.lifecycle, modelData)
      }
    }
  }

  private case class StandaloneInvokerCompiler(compiledProcess: CompiledProcess) {

    import cats.implicits._
    type CompilationValidation[K] = ValidatedNel[ProcessCompilationError, K]
    type CompilationResult[K] = ValidatedNel[ProcessCompilationError, K]

    private def compileWithCompilationErrors(node: SplittedNode[_], validationContext: ValidationContext) =
      compiledProcess.subPartCompiler.compile(node, validationContext).result

    private def lazyParameterInterpreter: CompilerLazyParameterInterpreter = new CompilerLazyParameterInterpreter {
      override def deps: LazyInterpreterDependencies = compiledProcess.lazyInterpreterDeps

      override def metaData: MetaData = compiledProcess.parts.metaData

      override def close(): Unit = {}
    }

    private def compiledPartInvoker(processPart: ProcessPart): data.ValidatedNel[ProcessCompilationError, InterpreterType] = processPart match {
      case SourcePart(_, node, validationContext, nextParts, _) =>
        compileWithCompilationErrors(node, validationContext).andThen(partInvoker(_, nextParts))
      case part@SinkPart(sinkWithParams:StandaloneSinkWithParameters, endNode, validationContext) =>
        val response = sinkWithParams.prepareResponse(lazyParameterInterpreter)
        Valid((ctx: Context, ec:ExecutionContext) => {
          response(ctx, ec).map(res => Right(List(InterpretationResult(EndReference(endNode.id), res, ctx))))(ec)
        })

      case part@SinkPart(_, endNode, validationContext) =>
        compileWithCompilationErrors(endNode, validationContext)
          .andThen(partInvoker(_, List()))
      case CustomNodePart(transformerObj, node, validationContext, parts, _) =>
        val validatedTransformer = transformerObj match {
          case t: StandaloneCustomTransformer => Valid(t)
          case ContextTransformation(_, t: StandaloneCustomTransformer) => Valid(t)
          case _ => Invalid(NonEmptyList.of(UnsupportedPart(node.id)))
        }
        validatedTransformer.andThen { transformer =>
          val result = compileWithCompilationErrors(node, validationContext).andThen(partInvoker(_, parts))
          result.map(transformer.createTransformation(node.data.outputVar.get)(_, lazyParameterInterpreter))
        }
    }

    private def compilePartInvokers(parts: List[SubsequentPart]) : CompilationResult[Map[String, InterpreterType]] =
      parts.map(part => compiledPartInvoker(part).map(compiled => part.id -> compiled))
        .sequence[CompilationResult, (String, InterpreterType)].map(_.toMap)

    private def partInvoker(node: compiledgraph.node.Node, parts: List[SubsequentPart]): data.ValidatedNel[ProcessCompilationError, InterpreterType] = {

      compilePartInvokers(parts).map(_.toMap).map { partsInvokers =>

        (ctx: Context, ec: ExecutionContext) => {
          implicit val iec: ExecutionContext = ec
          compiledProcess.interpreter.interpret(node, compiledProcess.parts.metaData, ctx).flatMap { maybeResult =>
            maybeResult.fold[InterpreterOutputType](
            ir => Future.sequence(ir.map(interpretationInvoke(partsInvokers))).map(foldResults),
            a => Future.successful(Left(NonEmptyList.of(a))))
          }
        }
      }
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

    def compile: ValidatedNel[ProcessCompilationError, InterpreterType] = compiledPartInvoker(compiledProcess.parts.sources.head)

  }

}




case class StandaloneProcessInterpreter(context: StandaloneContext,
                                        source: StandaloneSource[Any],
                                        private val invoker: types.InterpreterType,
                                        private val lifecycle: Seq[Lifecycle],
                                        private val modelData: ModelData) extends InvocationMetrics {

  val id: String = context.processId

  private val counter = new AtomicLong(0)

  def invoke(input: Any)(implicit ec: ExecutionContext): Future[GenericListResultType[Any]] = {
    invokeToResult(input).map(_.right.map(_.map(_.output)))
  }

  def invokeToResult(input: Any)(implicit ec: ExecutionContext): InterpreterOutputType = modelData.withThisAsContextClassLoader {
    val contextId = s"${context.processId}-${counter.getAndIncrement()}"
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
