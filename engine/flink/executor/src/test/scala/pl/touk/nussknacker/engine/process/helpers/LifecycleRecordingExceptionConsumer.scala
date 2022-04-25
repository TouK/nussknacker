package pl.touk.nussknacker.engine.process.helpers

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionConsumerProvider}
import pl.touk.nussknacker.engine.flink.test.RecordingExceptionConsumerProvider.recordingConsumerIdPath
import pl.touk.nussknacker.engine.flink.test.{RecordingExceptionConsumer, RecordingExceptionConsumerProvider, RunIdDataRecorder}

object LifecycleRecordingExceptionConsumerProvider {

  val providerName = "LifecycleRecordingException"

  def configWithProvider(config: Config, consumerId: String): Config =
    RecordingExceptionConsumerProvider.configWithProvider(config, consumerId)
      .withValue("exceptionHandler.type", fromAnyRef(providerName))

}

class LifecycleRecordingExceptionConsumerProvider extends FlinkEspExceptionConsumerProvider {

  import LifecycleRecordingExceptionConsumerProvider._
  import net.ceedubs.ficus.Ficus._

  override val name: String = providerName

  override def create(metaData: MetaData, exceptionHandlerConfig: Config): FlinkEspExceptionConsumer = {
    val id = exceptionHandlerConfig.as[String](recordingConsumerIdPath)
    new LifecycleRecordingExceptionConsumer(id)
  }
}

private[helpers] case class LifecycleRecord(opened: Boolean = false, closed: Boolean = false)

object LifecycleRecordingExceptionConsumer extends RunIdDataRecorder[LifecycleRecord]

class LifecycleRecordingExceptionConsumer(id: String) extends RecordingExceptionConsumer(id) {

  import LifecycleRecordingExceptionConsumer._

  override def open(context: EngineRuntimeContext): Unit = {
    super.open(context)
    add((id, LifecycleRecord(opened = true)))
  }

  override def close(): Unit = {
    super.close()
    add((id, LifecycleRecord(closed = true)))
  }

}
