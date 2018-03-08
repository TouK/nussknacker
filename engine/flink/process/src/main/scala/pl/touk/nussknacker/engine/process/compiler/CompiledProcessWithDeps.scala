package pl.touk.nussknacker.engine.process.compiler

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.nussknacker.engine.Interpreter
import pl.touk.nussknacker.engine.api.{JobData, MetaData}
import pl.touk.nussknacker.engine.api.process.AsyncExecutionContextPreparer
import pl.touk.nussknacker.engine.compile.{CompiledProcess, PartSubGraphCompilationError, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.compiledgraph.CompiledProcessParts
import pl.touk.nussknacker.engine.compiledgraph.node.Node
import pl.touk.nussknacker.engine.compiledgraph.part.SourcePart
import pl.touk.nussknacker.engine.definition.CustomNodeInvokerDeps
import pl.touk.nussknacker.engine.flink.api.RuntimeContextLifecycle
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionHandler
import pl.touk.nussknacker.engine.flink.api.process.FlinkProcessSignalSenderProvider
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode

import scala.concurrent.duration.FiniteDuration

class CompiledProcessWithDeps(compiledProcess: CompiledProcess,
                              val jobData: JobData,
                              val exceptionHandler: FlinkEspExceptionHandler,
                              val signalSenders: FlinkProcessSignalSenderProvider,
                              val asyncExecutionContextPreparer: AsyncExecutionContextPreparer,
                              val processTimeout: FiniteDuration
                             ) {

  def open(runtimeContext: RuntimeContext) : Unit = {
    compiledProcess.lifecycle.foreach {_.open(jobData)}
    compiledProcess.lifecycle.collect{
      case s:RuntimeContextLifecycle =>
        s.open(runtimeContext)
    }
  }

  def close() : Unit = {
    compiledProcess.close()
  }

  def compileSubPart(node: SplittedNode[_], validationContext: ValidationContext): Node = {
    validateOrFail(compiledProcess.subPartCompiler.compile(node, validationContext).result)
  }

  private def validateOrFail[T](validated: ValidatedNel[ProcessCompilationError, T]): T = validated match {
    case Valid(r) => r
    case Invalid(err) => throw new scala.IllegalArgumentException(err.toList.mkString("Compilation errors: ", ", ", ""))
  }

  val metaData: MetaData = compiledProcess.parts.metaData

  val interpreter : Interpreter = compiledProcess.interpreter

  val customNodeInvokerDeps: CustomNodeInvokerDeps = compiledProcess.customNodeInvokerDeps

  val source: SourcePart = compiledProcess.parts.source
}

