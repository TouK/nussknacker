package pl.touk.nussknacker.engine.api.process

import scala.concurrent.ExecutionContext

trait AsyncExecutionContextPreparer {

  def bufferSize: Int

  def defaultUseAsyncInterpretation: Option[Boolean]

  def prepareExecutionContext(processId: String, parallelism: Int): ExecutionContext

  def close(): Unit

}