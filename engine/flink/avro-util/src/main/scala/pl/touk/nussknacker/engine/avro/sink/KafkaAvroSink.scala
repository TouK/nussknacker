package pl.touk.nussknacker.engine.avro.sink

import org.apache.flink.streaming.api.datastream.DataStreamSink
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import pl.touk.nussknacker.engine.api.{InterpretationResult, LazyParameter}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSink}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PartitionByKeyFlinkKafkaProducer}

class KafkaAvroSink(topic: String, output: LazyParameter[Any], kafkaConfig: KafkaConfig, serializationSchema: KafkaSerializationSchema[Any], clientId: String)
  extends FlinkSink with Serializable {

  import org.apache.flink.streaming.api.scala._

  override def registerSink(dataStream: DataStream[InterpretationResult], flinkNodeContext: FlinkCustomNodeContext): DataStreamSink[_] =
    dataStream
      .map(_.finalContext)
      .map(flinkNodeContext.lazyParameterHelper.lazyMapFunction(output))
      .map(_.value)
      .addSink(toFlinkFunction)

  /**
    * Right now we don't support i, because we don't use default sink behavior with expression..
    */
  override def testDataOutput: Option[Any => String] = None

  private def toFlinkFunction: SinkFunction[Any] =
    PartitionByKeyFlinkKafkaProducer(kafkaConfig, topic, serializationSchema, clientId)
}
