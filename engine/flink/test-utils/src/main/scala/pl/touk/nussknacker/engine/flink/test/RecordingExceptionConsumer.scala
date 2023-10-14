package pl.touk.nussknacker.engine.flink.test

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.exception.{NonTransientException, NuExceptionInfo}
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionConsumerProvider}
import pl.touk.nussknacker.test.WithDataList

import java.util.UUID

trait RunIdDataRecorder[T] extends WithDataList[(String, T)] {

  def dataFor(id: String): List[T] =
    data.collect { case (eid, ex) if eid == id => ex }

  def clearData(id: String): Unit = {
    clear { case (eid, _) => eid == id }
  }

}

object RecordingExceptionConsumer extends RunIdDataRecorder[NuExceptionInfo[_ <: Throwable]]

class RecordingExceptionConsumer(id: String) extends FlinkEspExceptionConsumer {

  override def consume(exceptionInfo: NuExceptionInfo[NonTransientException]): Unit =
    RecordingExceptionConsumer.add((id, exceptionInfo))
}

object RecordingExceptionConsumerProvider {
  final val ProviderName: String             = "RecordingException"
  final val RecordingConsumerIdPath: String  = "recordingConsumerId"
  final val ExceptionHandlerTypePath: String = "exceptionHandler.type"

  def configWithProvider(config: Config, consumerId: String): Config =
    config
      .withValue(ExceptionHandlerTypePath, fromAnyRef(ProviderName))
      .withValue(s"exceptionHandler.$RecordingConsumerIdPath", fromAnyRef(consumerId))

}

class RecordingExceptionConsumerProvider extends FlinkEspExceptionConsumerProvider {
  import RecordingExceptionConsumerProvider._
  import net.ceedubs.ficus.Ficus._

  override val name: String = ProviderName

  override def create(metaData: MetaData, exceptionHandlerConfig: Config): FlinkEspExceptionConsumer = {
    val id = exceptionHandlerConfig.getOrElse[String](RecordingConsumerIdPath, UUID.randomUUID().toString)
    new RecordingExceptionConsumer(id)
  }

}
