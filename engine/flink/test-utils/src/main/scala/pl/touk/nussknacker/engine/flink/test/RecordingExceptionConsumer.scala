package pl.touk.nussknacker.engine.flink.test

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.exception.{NonTransientException, NuExceptionInfo}
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionConsumerProvider}
import pl.touk.nussknacker.engine.process.helpers.TestResultsHolder

import java.util.UUID

object RecordingExceptionConsumer extends TestResultsHolder[(String, NuExceptionInfo[_ <: Throwable])] {

  def createExceptionConsumer(id: String) = new RecordingExceptionConsumer(this, id)

  def exceptionsFor(id: String): List[NuExceptionInfo[_ <: Throwable]] =
    results.collect { case (eid, ex) if eid == id => ex }

  def clearRecordedExceptions(id: String): Unit = {
    clear { case (eid, _) => eid == id }
  }

}

class RecordingExceptionConsumer(
    exceptionsHolder: => TestResultsHolder[(String, NuExceptionInfo[_ <: Throwable])],
    id: String
) extends FlinkEspExceptionConsumer {

  override def consume(exceptionInfo: NuExceptionInfo[NonTransientException]): Unit =
    exceptionsHolder.add((id, exceptionInfo))

}

object RecordingExceptionConsumerProvider {
  final val providerName: String            = "RecordingException"
  final val recordingConsumerIdPath: String = "recordingConsumerId"

  def configWithProvider(config: Config, consumerId: String): Config =
    config
      .withValue("exceptionHandler.type", fromAnyRef(providerName))
      .withValue(s"exceptionHandler.$recordingConsumerIdPath", fromAnyRef(consumerId))

}

class RecordingExceptionConsumerProvider extends FlinkEspExceptionConsumerProvider {

  import net.ceedubs.ficus.Ficus._

  import RecordingExceptionConsumerProvider._

  override val name: String = providerName

  override def create(metaData: MetaData, exceptionHandlerConfig: Config): FlinkEspExceptionConsumer = {
    val id = exceptionHandlerConfig.getOrElse[String](recordingConsumerIdPath, UUID.randomUUID().toString)
    RecordingExceptionConsumer.createExceptionConsumer(id)
  }

}
