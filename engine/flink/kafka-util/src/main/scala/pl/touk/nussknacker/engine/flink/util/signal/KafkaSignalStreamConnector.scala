package pl.touk.nussknacker.engine.flink.util.signal

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.IngestionTimeExtractor
import org.apache.flink.streaming.api.scala.{ConnectedStreams, DataStream}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils}
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer

import scala.annotation.nowarn

trait KafkaSignalStreamConnector {
  val kafkaConfig: KafkaConfig
  val signalsTopic: String

  def connectWithSignals[A, B: TypeInformation](start: DataStream[A], processId: String, nodeId: String, schema: DeserializationSchema[B]): ConnectedStreams[A, B] = {
    @silent("deprecated")
    @nowarn("cat=deprecation")
    val signalsSource = new FlinkKafkaConsumer[B](signalsTopic, schema,
      KafkaUtils.toProperties(kafkaConfig, Some(s"$processId-$nodeId-signal")))
    val signalsStream = start.executionEnvironment
      .addSource(signalsSource).name(s"signals-$processId-$nodeId")
    val withTimestamps = assignTimestampsAndWatermarks(signalsStream)
    start.connect(withTimestamps)
  }

  //We use ingestion time here, to advance watermarks in connected streams
  //This is not always optimal solution, as e.g. in tests periodic watermarks are not the best option, so it can be overridden in implementations
  //Please note that *in general* it's not OK to assign standard event-based watermark, as signal streams usually
  //can be idle for long time. This prevent advancement of watermark on connected stream, which can lean to unexpected behaviour e.g. in aggregates
  @silent("deprecated")
  @nowarn("cat=deprecation")
  protected def assignTimestampsAndWatermarks[B](dataStream: DataStream[B]): DataStream[B] = {
    dataStream.assignTimestampsAndWatermarks(new IngestionTimeExtractor[B])
  }

}
