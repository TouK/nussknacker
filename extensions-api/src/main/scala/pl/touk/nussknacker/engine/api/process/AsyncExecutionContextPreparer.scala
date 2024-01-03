package pl.touk.nussknacker.engine.api.process

import scala.concurrent.ExecutionContext

// TODO: rename me to ServiceExecutionContextPreparer in the future (it will break the backward compatibility, so we
//       should do it together with other breaking API changes.
trait AsyncExecutionContextPreparer {

  def bufferSize: Int

  def defaultUseAsyncInterpretation: Option[Boolean]
  def prepare(processName: ProcessName): ServiceExecutionContext

  def close(): Unit
}

final case class ServiceExecutionContext(executionContext: ExecutionContext)
