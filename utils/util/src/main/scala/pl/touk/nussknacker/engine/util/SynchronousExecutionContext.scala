package pl.touk.nussknacker.engine.util

import java.util.concurrent.Executor

import scala.concurrent.ExecutionContext

object SynchronousExecutionContext {

  implicit val ctx : ExecutionContext = create()

  def create(): ExecutionContext = ExecutionContext.fromExecutor(new Executor {
    def execute(task: Runnable): Unit = task.run()
  })

}
