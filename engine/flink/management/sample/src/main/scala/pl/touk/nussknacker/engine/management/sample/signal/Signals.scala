package pl.touk.nussknacker.engine.management.sample.signal

import java.nio.charset.StandardCharsets

import pl.touk.nussknacker.engine.flink.util.source.EspDeserializationSchema
import argonaut.Argonaut._
import argonaut.ArgonautShapeless._

object Signals {
  case class SampleProcessSignal(processId: String, timestamp: Long, action: SignalAction)

  sealed trait SignalAction {
    def key: String
  }

  case class RemoveLock(lockId: String) extends SignalAction {
    override def key: String = lockId
  }

  object ProcessSignalCodecs {
    import argonaut.ArgonautShapeless._
    import argonaut.CodecJson
    import argonaut.derive._

    private implicit def typeFieldJsonSumCodecFor[S]: JsonSumCodecFor[S] =
      JsonSumCodecFor(JsonSumCodec.typeField)

    val signalActionCodec: CodecJson[SignalAction] = argonaut.CodecJson.derived[SignalAction]
    val processSignalCodec: CodecJson[SampleProcessSignal] = argonaut.CodecJson.derived[SampleProcessSignal]
  }

}

object SignalSchema {
  import Signals._
  import ProcessSignalCodecs._
  import org.apache.flink.streaming.api.scala._
  val deserializationSchema = new EspDeserializationSchema[SampleProcessSignal](jsonBytes => new String(jsonBytes,StandardCharsets.UTF_8).decodeOption(processSignalCodec.Decoder).get)
}