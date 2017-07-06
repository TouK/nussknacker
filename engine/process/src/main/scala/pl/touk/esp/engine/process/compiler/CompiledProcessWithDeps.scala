package pl.touk.esp.engine.process.compiler

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.esp.engine.Interpreter
import pl.touk.esp.engine.api.exception.EspExceptionInfo
import pl.touk.esp.engine.api.{MetaData, ProcessListener, Service}
import pl.touk.esp.engine.compile.{PartSubGraphCompilationError, PartSubGraphCompiler}
import pl.touk.esp.engine.compiledgraph.CompiledProcessParts
import pl.touk.esp.engine.compiledgraph.node.Node
import pl.touk.esp.engine.definition.CustomNodeInvokerDeps
import pl.touk.esp.engine.flink.api.exception.FlinkEspExceptionHandler
import pl.touk.esp.engine.flink.api.process.{FlinkCustomNodeContext, FlinkProcessSignalSenderProvider}
import pl.touk.esp.engine.process.WithLifecycle
import pl.touk.esp.engine.splittedgraph.splittednode.SplittedNode

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

case class CompiledProcessWithDeps(compiledProcess: CompiledProcessParts,
                                   private val services: WithLifecycle[Service],
                                   private val listeners: WithLifecycle[ProcessListener],
                                   subPartCompiler: PartSubGraphCompiler,
                                   interpreter: Interpreter,
                                   processTimeout: FiniteDuration,
                                   signalSenders: FlinkProcessSignalSenderProvider
                                  ) extends CustomNodeInvokerDeps {

  def open(runtimeContext: RuntimeContext)(implicit ec: ExecutionContext): Unit = {
    services.open(runtimeContext)
    listeners.open(runtimeContext)
    exceptionHandler.open(runtimeContext)
  }

  def close() = {
    services.close()
    listeners.close()
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
  
  val exceptionHandler: FlinkEspExceptionHandler = new ListeningExceptionHandler

  private class ListeningExceptionHandler extends FlinkEspExceptionHandler {

    //FIXME: remove casting...
    private def flinkExceptionHandler = compiledProcess.exceptionHandler.asInstanceOf[FlinkEspExceptionHandler]

    override def open(runtimeContext: RuntimeContext) = {
      flinkExceptionHandler.open(runtimeContext)
    }

    override def close() = {
      flinkExceptionHandler.close()
    }

    override def restartStrategy = flinkExceptionHandler.restartStrategy

    override def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]) = {
      listeners.values.foreach(_.exceptionThrown(exceptionInfo))
      compiledProcess.exceptionHandler.handle(exceptionInfo)
    }
  }

}

