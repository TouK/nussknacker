package pl.touk.nussknacker.engine.management.sample.signal

import java.nio.charset.StandardCharsets

import io.circe.generic.JsonCodec
import io.circe.generic.extras.ConfiguredJsonCodec
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.flink.util.source.EspDeserializationSchema

object Signals {
  @JsonCodec case class SampleProcessSignal(processId: String, timestamp: Long, action: SignalAction)

  //Note: due to some problems with circe (https://circe.github.io/circe/codecs/known-issues.html#knowndirectsubclasses-error)
  // and scala 2.11 this definition should be *before* SignalAction. This problem occurs during doc generation...
  case class RemoveLock(lockId: String) extends SignalAction {
    override def key: String = lockId
  }

  @ConfiguredJsonCodec sealed trait SignalAction {
    def key: String
  }

}

object SignalSchema {
  import Signals._
  import org.apache.flink.streaming.api.scala._
  val deserializationSchema = new EspDeserializationSchema[SampleProcessSignal](jsonBytes => decodeJsonUnsafe[SampleProcessSignal](jsonBytes))
}