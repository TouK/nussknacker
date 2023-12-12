package pl.touk.nussknacker.engine.api.process

import scala.concurrent.ExecutionContext

trait ServiceExecutionContextPreparer {

  def bufferSize: Int

  def defaultUseAsyncInterpretation: Option[Boolean]

  def prepare(processId: String): ServiceExecutionContext

  def close(): Unit
}

final case class ServiceExecutionContext(executionContext: ExecutionContext)
