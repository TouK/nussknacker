package pl.touk.nussknacker.engine.api.process

import scala.concurrent.ExecutionContext

trait AsyncExecutionContextPreparer {

  def bufferSize: Int

  def prepareExecutionContext(processId: String, parallelism: Int): ExecutionContext

  def close(): Unit

}