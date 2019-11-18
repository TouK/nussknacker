package pl.touk.nussknacker.engine.flink.util.signal

import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.scala.{ConnectedStreams, DataStream}
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer011
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaEspUtils}

trait KafkaSignalStreamConnector {
  val kafkaConfig: KafkaConfig
  val signalsTopic: String

  def connectWithSignals[A, B: TypeInformation](start: DataStream[A], processId: String, nodeId: String, schema: DeserializationSchema[B]): ConnectedStreams[A, B] = {
    val signalsSource = new FlinkKafkaConsumer011[B](signalsTopic, schema,
      KafkaEspUtils.toProperties(kafkaConfig, Some(s"$processId-$nodeId-signal")))
    val signals = start.executionEnvironment.addSource(signalsSource).name(s"signals-$processId-$nodeId")
    start.connect(signals)
  }
}
