package pl.touk.esp.engine.standalone

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

import cats.data.Validated.{Invalid, Valid}
import cats.data.{Validated, ValidatedNel, Xor}
import com.typesafe.config.Config
import pl.touk.esp.engine.Interpreter
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.api.exception.EspExceptionHandler
import pl.touk.esp.engine.api.process.{ProcessConfigCreator, StandaloneSourceFactory}
import pl.touk.esp.engine.api.test.InvocationCollectors.{NodeContext, SinkInvocationCollector}
import pl.touk.esp.engine.compile.{PartSubGraphCompiler, ProcessCompilationError, ProcessCompiler}
import pl.touk.esp.engine.compiledgraph.CompiledProcessParts
import pl.touk.esp.engine.compiledgraph.node.Node
import pl.touk.esp.engine.compiledgraph.part.{CustomNodePart, SinkPart, SplitPart}
import pl.touk.esp.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.graph.node.NodeData
import pl.touk.esp.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.esp.engine.util.SynchronousExecutionContext

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

class StandaloneProcessInterpreter(process: EspProcess)
                                  (creator: ProcessConfigCreator, config: Config,
                                   sinkInvocationCollector: Option[SinkInvocationCollector] = None,
                                   additionalListeners: List[ProcessListener] = List(),
                                   definitionsPostProcessor: (ProcessDefinitionExtractor.ProcessDefinition[ObjectWithMethodDef] => ProcessDefinitionExtractor.ProcessDefinition[ObjectWithMethodDef]) = identity) {

  import pl.touk.esp.engine.util.Implicits._

  private val definitions = definitionsPostProcessor(ProcessDefinitionExtractor.extractObjectWithMethods(creator, config))
  private val services = creator.services(config).map { case (_, service) => service.value }
  //for testing environment it's important to take classloader from user jar
  private val sub = PartSubGraphCompiler.default(definitions.services, definitions.globalVariables.mapValuesNow(_.value), creator.getClass.getClassLoader)
  private val compiler = new ProcessCompiler(sub, definitions)
  private val interpreter = Interpreter.apply(definitions.services, FiniteDuration(10, TimeUnit.SECONDS), creator.listeners(config) ++ additionalListeners)
  private val compiled = compiler.compile(process)
  def exceptionHandler: EspExceptionHandler = validateOrFail(compiled.map(_.exceptionHandler))

  private val counter = new AtomicLong(0)

  def open()(implicit ec: ExecutionContext): Unit = {
    services.foreach(_.open())
    //fixme moze najpierw kompilowac caly graf i potem uruchamiac juz na skompilowanym, an nie za kazdym razem kompilowac?
    //fixme jak zawolam open() w czasie testowania to metryki beda sie inicjalizowac?
  }

  def validateProcess(): Xor[String, Unit] = {
    singleErrorMessage(compiled).toXor.map(_ => ())
  }

  def close(): Unit = {
    services.foreach(_.close())
  }

  def source[T] = {
    definitions.sourceFactories(process.root.data.ref.typ).obj.asInstanceOf[StandaloneSourceFactory[T]]
  }

  def run(input: Any)(implicit ec: ExecutionContext): Future[Option[Any]] = {
    val result = compiled.map { parts => new InterpreterInvoker(parts, None).invoke(input)}
    validateOrFail(result).map(_.map(_.output))
  }

  def runSync(input: Any)(timeout: FiniteDuration, espExceptionHandler: EspExceptionHandler): Option[Any] = {
    implicit val ec = SynchronousExecutionContext.ctx
    val result = compiled
      .map { parts => new InterpreterInvoker(parts, Some(SyncInvocationParams(timeout, espExceptionHandler))).invoke(input)}
      .map(res => Await.result(res, timeout))
    val output = validateOrFail(result).map(_.output)
    output
  }

  case class SyncInvocationParams(timeout: FiniteDuration, exceptionHandler: EspExceptionHandler)

  class InterpreterInvoker(parts: CompiledProcessParts, syncParams: Option[SyncInvocationParams]) {

    def invoke(input: Any)(implicit ec: ExecutionContext): Future[Option[InterpretationResult]] = {
      val source = validateOrFail(sub.compileWithoutContextValidation(parts.source.node).map(_.node))
      val contextId = s"${parts.metaData.id}-${counter.getAndIncrement()}"
      val ctx = Context(contextId).withVariable(Interpreter.InputParamName, input)
      val sourcePartResult = interpret(source, ctx)
      mapAndInterpretNext(sourcePartResult)
    }

    private def interpretNext(ir: InterpretationResult)(implicit ec: ExecutionContext): Future[Option[InterpretationResult]] = {

      def interptetLoop[T <: NodeData](splittedNode: SplittedNode[T], previousInterpretation: InterpretationResult)
                                      (implicit ec: ExecutionContext): Future[Option[InterpretationResult]] = {
        val compiledNode = validateOrFail(sub.compileWithoutContextValidation(splittedNode).map(_.node))
        val newResult = interpret(compiledNode, previousInterpretation.finalContext)
        mapAndInterpretNext(newResult)
      }
      ir.reference match {
        case _: EndReference =>
          Future.successful(Some(ir))
        case _: DeadEndReference =>
          Future.successful(None)
        case NextPartReference(partId) =>
          val nextPart = parts.source.nextParts.find(_.id == partId).get
          nextPart match {
            case sinkPart: SinkPart =>
              val res = interptetLoop(sinkPart.node, ir)
              res.foreach { resOpt =>
                resOpt.foreach { res =>
                  sinkInvocationCollector.foreach(_.collect(res.output, NodeContext(res.finalContext.id, sinkPart.node.id, sinkPart.node.data.ref.typ), sinkPart.obj))
                }
              }
              res
            case _: CustomNodePart =>
              throw new UnsupportedOperationException("CustomNodePart unsupported in standalone mode")
            case _: SplitPart =>
              throw new UnsupportedOperationException("SplitPart unsupported in standalone mode")
          }
      }
    }

    private def mapAndInterpretNext[T <: NodeData](newResult: Future[Option[InterpretationResult]])(implicit ec: ExecutionContext) = {
      newResult.flatMap {
        case Some(res) =>
          interpretNext(res)
        case None =>
          Future.successful(None)
      }
    }

    private def interpret(node: Node, ctx: Context)(implicit executor: ExecutionContext): Future[Option[InterpretationResult]] = {
      syncParams match {
        case None =>
          interpreter.interpret(node, InterpreterMode.Traverse, process.metaData, ctx).map(Some(_))
        case Some(params) =>
          Future.successful(interpreter.interpretSync(node, InterpreterMode.Traverse, process.metaData, ctx, params.timeout, params.exceptionHandler))
      }
    }
  }

  private def validateOrFail[Error <: ProcessCompilationError, T](validated: ValidatedNel[Error, T]): T = {
    singleErrorMessage(validated) match {
      case Valid(r) => r
      case Invalid(errorMsg) => throw new scala.IllegalArgumentException(errorMsg)
    }
  }

  private def singleErrorMessage[Error <: ProcessCompilationError, T](validated: ValidatedNel[Error, T]): Validated[String, T] = {
    validated.leftMap(errors => errors.toList.mkString("Compilation errors: ", ", ", ""))
  }


}
