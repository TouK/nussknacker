package pl.touk.nussknacker.engine.avro.sink

import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.flink.streaming.api.datastream.DataStreamSink
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import pl.touk.nussknacker.engine.api.{InterpretationResult, LazyParameter}
import pl.touk.nussknacker.engine.avro.encode.BestEffortAvroEncoder
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSink}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PartitionByKeyFlinkKafkaProducer}

class KafkaAvroSink(topic: String, output: LazyParameter[Any], kafkaConfig: KafkaConfig, schema: Schema, serializationSchema: KafkaSerializationSchema[Any], clientId: String)
  extends FlinkSink with Serializable with LazyLogging {

  import org.apache.flink.streaming.api.scala._

  override def registerSink(dataStream: DataStream[InterpretationResult], flinkNodeContext: FlinkCustomNodeContext): DataStreamSink[_] = {
    dataStream
      .map(_.finalContext)
      .map(flinkNodeContext.lazyParameterHelper.lazyMapFunction(output))
      .map(ctx => ctx.value match {
          case data: java.util.Map[String, Any] => BestEffortAvroEncoder.encodeRecordOrError(data, schema)
          case _: GenericRecord => ctx.value
          case value => {
            logger.error("Invalid output type error.", ctx)
            throw InvalidSinkOutput(s"Invalid output type ${value.getClass}. Excepted java.util.Map or GenericRecord.")
          }
        }
      )
      .addSink(toFlinkFunction)
  }

  /**
    * Right now we don't support it, because we don't use default sink behavior with expression..
    */
  override def testDataOutput: Option[Any => String] = None

  private def toFlinkFunction: SinkFunction[Any] =
    PartitionByKeyFlinkKafkaProducer(kafkaConfig, topic, serializationSchema, clientId)
}
