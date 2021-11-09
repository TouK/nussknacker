package pl.touk.nussknacker.engine.process.registrar

import com.typesafe.config.Config
import org.apache.flink.api.common.functions.RuntimeContext
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.{JobData, MetaData, ProcessListener}
import pl.touk.nussknacker.engine.flink.api.exception.{DelegatingFlinkEspExceptionHandler, FlinkEspExceptionHandler}
import pl.touk.nussknacker.engine.flink.util.exception.FlinkExceptionHandler
import pl.touk.nussknacker.engine.flink.util.listener.NodeCountMetricListener
import pl.touk.nussknacker.engine.process.compiler.FlinkEngineRuntimeContextImpl
import pl.touk.nussknacker.engine.util.LoggingListener

case class DupaDupaFactory(config: Config, listeners: Seq[ProcessListener]) {

  private class ListeningExceptionHandler(listeners: Seq[ProcessListener], exceptionHandler: FlinkEspExceptionHandler)
    extends DelegatingFlinkEspExceptionHandler(exceptionHandler) {

    override def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit = {
      listeners.foreach(_.exceptionThrown(exceptionInfo))
      delegate.handle(exceptionInfo)
    }
  }

  def create(jobData: JobData, metaData: MetaData, classLoader: ClassLoader): DupaDupa = {
    val handler = new FlinkExceptionHandler(metaData, config, classLoader, listeners)
    val listeningHandler = new ListeningExceptionHandler(aaListeners(), handler)
    DupaDupa(
      handler = listeningHandler,
      jobData
    )
  }

  protected def aaListeners(): Seq[ProcessListener] = {
    //TODO: should this be configurable somehow?
    //if it's configurable, it also has to affect NodeCountMetricFunction!
    List(LoggingListener, new NodeCountMetricListener) ++ listeners
  }
}

case class DupaDupa(handler: FlinkEspExceptionHandler, jobData: JobData) {

  def prepare(runtimeContext: RuntimeContext): FlinkEspExceptionHandler = {
    handler.open(FlinkEngineRuntimeContextImpl(jobData, runtimeContext))
    handler
  }
}
