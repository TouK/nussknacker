package pl.touk.esp.engine.standalone

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

import cats.data
import cats.data.Validated.Invalid
import cats.data.{NonEmptyList, ValidatedNel}
import com.typesafe.config.Config
import pl.touk.esp.engine.Interpreter
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.api.exception.EspExceptionInfo
import pl.touk.esp.engine.api.process.{ProcessConfigCreator, StandaloneSourceFactory}
import pl.touk.esp.engine.compile.PartSubGraphCompilerBase.CompiledNode
import pl.touk.esp.engine.compile.ProcessCompilationError.UnsupportedPart
import pl.touk.esp.engine.compile.{PartSubGraphCompiler, ProcessCompilationError, ProcessCompiler}
import pl.touk.esp.engine.compiledgraph.CompiledProcessParts
import pl.touk.esp.engine.compiledgraph.part._
import pl.touk.esp.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.esp.engine.standalone.StandaloneProcessInterpreter.OutType

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

object StandaloneProcessInterpreter {

  type ResultType = Option[InterpretationResult]

  type OutType = Future[Either[ResultType, EspExceptionInfo[_ <: Throwable]]]

  type OutFunType = (Context, ExecutionContext) => OutType

  def apply(process: EspProcess, creator: ProcessConfigCreator, config: Config,
            additionalListeners: List[ProcessListener] = List(),
            definitionsPostProcessor: (ProcessDefinitionExtractor.ProcessDefinition[ObjectWithMethodDef]
              => ProcessDefinitionExtractor.ProcessDefinition[ObjectWithMethodDef]) = identity)
  : ValidatedNel[ProcessCompilationError, StandaloneProcessInterpreter] = {

    import pl.touk.esp.engine.util.Implicits._

    val definitions = definitionsPostProcessor(ProcessDefinitionExtractor.extractObjectWithMethods(creator, config))
    val services = creator.services(config).map { case (_, service) => service.value }

    //for testing environment it's important to take classloader from user jar
    val sub = PartSubGraphCompiler.default(definitions.services, definitions.globalVariables.mapValuesNow(_.value), creator.getClass.getClassLoader)
    val interpreter = Interpreter.apply(definitions.services, FiniteDuration(10, TimeUnit.SECONDS), creator.listeners(config) ++ additionalListeners)

    new ProcessCompiler(sub, definitions).compile(process)
      .andThen(StandaloneInvokerCompiler(sub, _, interpreter).compile)
      .map(StandaloneProcessInterpreter(process.id, _, services,
        definitions.sourceFactories(process.root.data.ref.typ).obj.asInstanceOf[StandaloneSourceFactory[Any]]))

  }

  private case class StandaloneInvokerCompiler(sub: PartSubGraphCompiler,
                                       compiled: CompiledProcessParts, interpreter: Interpreter) {

    type CompilationResult[K] = ValidatedNel[ProcessCompilationError, K]

    import cats.implicits._

    //NonEmptyList is invariant :(
    private def compileWithCompilationErrors(node: SplittedNode[_]) =
      sub.compileWithoutContextValidation(node).bimap(_.map(_.asInstanceOf[ProcessCompilationError]), identity)

    private def compiledPartInvoker(processPart: ProcessPart): data.ValidatedNel[ProcessCompilationError, OutFunType] = processPart match {
      case SourcePart(source, node, nextParts, ends) =>
        compileWithCompilationErrors(node).andThen(partInvoker(_, nextParts))
      case part@SinkPart(_, endNode) =>
        compileWithCompilationErrors(endNode).andThen(partInvoker(_, List()))
      case a: SplitPart => Invalid(NonEmptyList.of(UnsupportedPart(a.id)))
      case a: CustomNodePart => Invalid(NonEmptyList.of(UnsupportedPart(a.id)))
    }

    private def compilePartInvokers(parts: List[SubsequentPart]) : CompilationResult[Map[String, OutFunType]] =
      parts.map(part => compiledPartInvoker(part).map(compiled => part.id -> compiled))
        .sequence[CompilationResult, (String, OutFunType)].map(_.toMap)

    private def partInvoker(node: CompiledNode, parts: List[SubsequentPart]): data.ValidatedNel[ProcessCompilationError, OutFunType] = {

      compilePartInvokers(parts).map(_.toMap).map { partsInvokers =>

        (ctx: Context, ec: ExecutionContext) => {
          implicit val iec = ec
          interpreter.interpret(node.node, InterpreterMode.Traverse, compiled.metaData, ctx).flatMap { maybeResult =>
            maybeResult.fold[OutType](ir => {
              ir.reference match {
                case _: EndReference =>
                  Future.successful(Left(Some(ir)))
                case _: DeadEndReference =>
                  Future.successful(Left(None))
                case NextPartReference(id) =>
                  partsInvokers.getOrElse(id, throw new Exception("Unknown reference"))(ir.finalContext, ec)
              }
            }, a => Future.successful(Right(a)))
          }
        }
      }
    }

    def compile: ValidatedNel[ProcessCompilationError, OutFunType] = compiledPartInvoker(compiled.source)

  }

}


case class StandaloneProcessInterpreter(id: String,
                                        invoker: StandaloneProcessInterpreter.OutFunType,
                                        services: Iterable[Service],
                                        source: StandaloneSourceFactory[Any]) {

  private val counter = new AtomicLong(0)

  def invoke(input: Any)(implicit ec: ExecutionContext): Future[Either[Option[Any], EspExceptionInfo[_ <: Throwable]]] = {
    invokeToResult(input).map(_.left.map(_.map(_.output)))
  }

  def invokeToResult(input: Any)(implicit ec: ExecutionContext): OutType = {
    val contextId = s"$id-${counter.getAndIncrement()}"
    val ctx = Context(contextId).withVariable(Interpreter.InputParamName, input)
    invoker(ctx, ec)
  }

  def open()(implicit ec: ExecutionContext): Unit = {
    services.foreach(_.open())
    //fixme jak zawolam open() w czasie testowania to metryki beda sie inicjalizowac?
  }

  def close(): Unit = {
    services.foreach(_.close())
  }


}
