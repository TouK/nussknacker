package pl.touk.nussknacker.engine.api.process

import scala.concurrent.ExecutionContext

/**
  * * prepareExecutionContext will be called multiple times (for each asynch function subtask)
  * * close will be called only once in UserCodeClassLoaderReleaseHook (look pl.touk.nussknacker.engine.process.registrar.AsyncInterpretationFunction)
  */
trait AsyncExecutionContextPreparer {

  def bufferSize: Int

  def defaultUseAsyncInterpretation: Option[Boolean]

  def prepareExecutionContext(processId: String, parallelism: Int): ExecutionContext

  def close(): Unit

}
