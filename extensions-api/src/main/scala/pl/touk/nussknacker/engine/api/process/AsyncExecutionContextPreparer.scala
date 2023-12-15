package pl.touk.nussknacker.engine.api.process

import scala.concurrent.ExecutionContext

// TODO: rename me to ServiceExecutionContextPreparer in the future
trait AsyncExecutionContextPreparer {

  def bufferSize: Int

  def defaultUseAsyncInterpretation: Option[Boolean]

  @deprecated("Use 'prepare' instead")
  def prepareExecutionContext(processId: String, parallelism: Int): ExecutionContext =
    prepare(processId).executionContext

  def prepare(processId: String): ServiceExecutionContext

  def close(): Unit
}

final case class ServiceExecutionContext(executionContext: ExecutionContext)
