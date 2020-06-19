package pl.touk.nussknacker.engine.avro.sink

import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.Schema
import org.apache.avro.generic.GenericContainer
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

  // We don't wan serialize it because of flink serialization..
  @transient final private lazy val avroEncoder = BestEffortAvroEncoder()

  override def registerSink(dataStream: DataStream[InterpretationResult], flinkNodeContext: FlinkCustomNodeContext): DataStreamSink[_] = {
    dataStream
      .map(_.finalContext)
      .map(flinkNodeContext.lazyParameterHelper.lazyMapFunction(output))
      .map(ctx => ctx.value match {
          case data: java.util.Map[String, Any] => avroEncoder.encodeRecordOrError(data, schema)
          case _: GenericContainer => ctx.value
          case _ => {
            //TODO: We should better handle this situation by using EspExceptionHandler
            logger.error("Invalid output type error.", ctx)
            null
          }
        }
      )
      .filter(_ != null)
      .addSink(toFlinkFunction)
  }

  /**
    * Right now we don't support it, because we don't use default sink behavior with expression..
    */
  override def testDataOutput: Option[Any => String] = None

  private def toFlinkFunction: SinkFunction[Any] =
    PartitionByKeyFlinkKafkaProducer(kafkaConfig, topic, serializationSchema, clientId)
}
