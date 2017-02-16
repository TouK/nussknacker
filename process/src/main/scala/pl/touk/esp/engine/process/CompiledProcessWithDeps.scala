package pl.touk.esp.engine.process

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.esp.engine.Interpreter
import pl.touk.esp.engine.api.{MetaData, ProcessListener}
import pl.touk.esp.engine.api.exception.{EspExceptionHandler, EspExceptionInfo}
import pl.touk.esp.engine.compile.{PartSubGraphCompilationError, PartSubGraphCompiler, ValidationContext}
import pl.touk.esp.engine.compiledgraph.CompiledProcessParts
import pl.touk.esp.engine.compiledgraph.node.Node
import pl.touk.esp.engine.definition.CustomNodeInvokerDeps
import pl.touk.esp.engine.flink.api.exception.FlinkEspExceptionHandler
import pl.touk.esp.engine.splittedgraph.splittednode.SplittedNode

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

case class CompiledProcessWithDeps(compiledProcess: CompiledProcessParts,
                                   private val servicesLifecycle: ServicesLifecycle,
                                   private val listeners: Seq[ProcessListener],
                                   subPartCompiler: PartSubGraphCompiler,
                                   interpreter: Interpreter,
                                   processTimeout: FiniteDuration) extends CustomNodeInvokerDeps {

  def open(runtimeContext: RuntimeContext)(implicit ec: ExecutionContext): Unit = {
    servicesLifecycle.open(runtimeContext)
    exceptionHandler.open(runtimeContext)
  }

  def close() = {
    servicesLifecycle.close()
    exceptionHandler.close()
  }

  def compileSubPart(node: SplittedNode[_]): Node = {
    validateOrFail(subPartCompiler.compileWithoutContextValidation(node).map(_.node))
  }

  private def validateOrFail[T](validated: ValidatedNel[PartSubGraphCompilationError, T]): T = validated match {
    case Valid(r) => r
    case Invalid(err) => throw new scala.IllegalArgumentException(err.toList.mkString("Compilation errors: ", ", ", ""))
  }

  def metaData: MetaData = compiledProcess.metaData

  def exceptionHandler: FlinkEspExceptionHandler = new ListeningExceptionHandler

  private class ListeningExceptionHandler extends FlinkEspExceptionHandler {

    //FIXME: ladniej bez castow...
    override def restartStrategy = compiledProcess.exceptionHandler.asInstanceOf[FlinkEspExceptionHandler].restartStrategy

    override def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]) = {
      listeners.foreach(_.exceptionThrown(exceptionInfo))
      compiledProcess.exceptionHandler.handle(exceptionInfo)
    }
  }

}

