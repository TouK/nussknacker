package pl.touk.nussknacker.engine.flink.api

import org.apache.flink.api.common.TaskInfo
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.connector.sink2.WriterInitContext
import org.apache.flink.metrics.groups.OperatorMetricGroup
import pl.touk.nussknacker.engine.flink.api.exception.ExceptionHandler

sealed trait FlinkEngineContext {
  def getTaskInfo: TaskInfo
  def getMetricGroup: OperatorMetricGroup
  def getUserCodeClassLoader: ClassLoader
}

final case class RuntimeCtx(ctx: RuntimeContext) extends FlinkEngineContext {

  override def getTaskInfo: TaskInfo = ctx.getTaskInfo

  override def getMetricGroup: OperatorMetricGroup = ctx.getMetricGroup

  override def getUserCodeClassLoader: ClassLoader = ctx.getUserCodeClassLoader
}

final case class WriterInitCtx(ctx: WriterInitContext) extends FlinkEngineContext {

  override def getTaskInfo: TaskInfo = ctx.getTaskInfo

  override def getMetricGroup: OperatorMetricGroup = ctx.metricGroup()

  override def getUserCodeClassLoader: ClassLoader = ctx.getUserCodeClassLoader.asClassLoader()
}

object FlinkEngineContextOps {

  implicit class RichExceptionHandler(val f: FlinkEngineContext => ExceptionHandler) extends AnyVal {
    def narrowToRuntimeCtx: RuntimeContext => ExceptionHandler =
      rc => f(RuntimeCtx(rc))

    def narrowToWriterInitCtx: WriterInitContext => ExceptionHandler =
      wc => f(WriterInitCtx(wc))
  }

}
