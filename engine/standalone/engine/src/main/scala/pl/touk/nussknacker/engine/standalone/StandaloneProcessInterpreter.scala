package pl.touk.nussknacker.engine.standalone

import java.util.concurrent.atomic.AtomicLong

import cats.data
import cats.data.Validated.Invalid
import cats.data.{NonEmptyList, ValidatedNel}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.compile.ProcessCompilationError.{MissingPart, UnsupportedPart}
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.compiledgraph.part._
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.{CustomNodeInvoker, ProcessDefinitionExtractor}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.Source
import pl.touk.nussknacker.engine.splittedgraph.splittednode.{NextNode, PartRef, SplittedNode}
import pl.touk.nussknacker.engine.standalone.api.types._
import pl.touk.nussknacker.engine.standalone.api.{StandaloneCustomTransformer, StandaloneSource, StandaloneSourceFactory, types}
import pl.touk.nussknacker.engine.standalone.metrics.InvocationMetrics
import pl.touk.nussknacker.engine.standalone.utils.{StandaloneContext, StandaloneContextLifecycle, StandaloneContextPreparer}
import pl.touk.nussknacker.engine.{Interpreter, ModelData, compiledgraph}

import scala.concurrent.duration._
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
            definitionsPostProcessor: (ProcessDefinitionExtractor.ProcessDefinition[ObjectWithMethodDef]
              => ProcessDefinitionExtractor.ProcessDefinition[ObjectWithMethodDef]) = identity)
  : ValidatedNel[ProcessCompilationError, StandaloneProcessInterpreter] = modelData.withThisAsContextClassLoader {

    val creator = modelData.configCreator
    val config = modelData.processConfig

    val definitions = definitionsPostProcessor(ProcessDefinitionExtractor.extractObjectWithMethods(creator, config))
    val listeners = creator.listeners(config) ++ additionalListeners

    CompiledProcess.compile(process,
      definitions,
      listeners,
      //FIXME: timeout
      modelData.modelClassLoader.classLoader, 10 seconds
    ).andThen { compiledProcess =>
      val source = compiledProcess.parts.source.obj.asInstanceOf[StandaloneSource[Any]]
      StandaloneInvokerCompiler(compiledProcess).compile.map { invoker =>
        StandaloneProcessInterpreter(contextPreparer.prepare(process.id), source, invoker, compiledProcess.lifecycle, modelData)
      }
    }
  }

  private case class StandaloneInvokerCompiler(compiledProcess: CompiledProcess) {

    import cats.implicits._
    type CompilationValidation[K] = ValidatedNel[ProcessCompilationError, K]
    type CompilationResult[K] = ValidatedNel[ProcessCompilationError, K]

    private def compileWithCompilationErrors(node: SplittedNode[_], validationContext: ValidationContext, nextValidationContext: Option[ValidationContext] = None) =
      compiledProcess.subPartCompiler.compile(node, validationContext, nextValidationContext).result

    private def compiledPartInvoker(processPart: ProcessPart): data.ValidatedNel[ProcessCompilationError, InterpreterType] = processPart match {
      case SourcePart(_, node, validationContext, nextParts, _) =>
        compileWithCompilationErrors(node, validationContext).andThen(partInvoker(_, nextParts))
      case part@SinkPart(_, endNode, validationContext) =>
        compileWithCompilationErrors(endNode, validationContext).andThen(partInvoker(_, List()))
      //TODO: does it have to be so complicated? here and in FlinkProcessRegistrar
      case SplitPart(_, validationContext, nexts) =>
        val splitParts = nexts.map {
          case NextWithParts(NextNode(node), parts, _) => compileWithCompilationErrors(node, validationContext).andThen(partInvoker(_, parts))
          case NextWithParts(PartRef(id), parts, _) => parts.find(_.id == id) match {
            case Some(part) => compiledPartInvoker(part)
            case None => Invalid(NonEmptyList.of[ProcessCompilationError](MissingPart(id)))
          }
        }.sequence[CompilationResult, InterpreterType]
        splitParts.map { compiledSplitParts =>
          (ctx: Context, ec: ExecutionContext) => {
            implicit val iec = ec
            compiledSplitParts.map(_ (ctx, ec))
              .sequence[Future, InterpretationResultType].map(StandaloneProcessInterpreter.foldResults)
          }
        }

      case CustomNodePart(executor:
                CustomNodeInvoker[StandaloneCustomTransformer@unchecked], node, validationContext, nextValidationContext, parts, _) =>
        val result = compileWithCompilationErrors(node, validationContext, Some(nextValidationContext)).andThen(partInvoker(_, parts))

        val transformer = executor.run(() => compiledProcess.customNodeInvokerDeps)
        result.map(transformer.createTransformation(node.data.outputVar.get))
      case a: CustomNodePart => Invalid(NonEmptyList.of(UnsupportedPart(a.id)))

    }

    private def compilePartInvokers(parts: List[SubsequentPart]) : CompilationResult[Map[String, InterpreterType]] =
      parts.map(part => compiledPartInvoker(part).map(compiled => part.id -> compiled))
        .sequence[CompilationResult, (String, InterpreterType)].map(_.toMap)

    private def partInvoker(node: compiledgraph.node.Node, parts: List[SubsequentPart]): data.ValidatedNel[ProcessCompilationError, InterpreterType] = {

      compilePartInvokers(parts).map(_.toMap).map { partsInvokers =>

        (ctx: Context, ec: ExecutionContext) => {
          implicit val iec = ec
          compiledProcess.interpreter.interpret(node, compiledProcess.parts.metaData, ctx).flatMap { maybeResult =>
            maybeResult.fold[InterpreterOutputType](ir => {
              ir.reference match {
                case _: EndReference =>
                  Future.successful(Right(List(ir)))
                case _: DeadEndReference =>
                  Future.successful(Right(Nil))
                case NextPartReference(id) =>
                  partsInvokers.getOrElse(id, throw new Exception("Unknown reference"))(ir.finalContext, ec)
              }
            }, a => Future.successful(Left(NonEmptyList.of(a))))
          }
        }
      }
    }

    def compile: ValidatedNel[ProcessCompilationError, InterpreterType] = compiledPartInvoker(compiledProcess.parts.source)

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
