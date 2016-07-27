package pl.touk.esp.engine.process.util

import java.util.concurrent.Executor

import scala.concurrent.ExecutionContext

object SynchronousExecutionContext {

  implicit val ctx : ExecutionContext = ExecutionContext.fromExecutor(new Executor {
    def execute(task: Runnable) = task.run()
  })

}
