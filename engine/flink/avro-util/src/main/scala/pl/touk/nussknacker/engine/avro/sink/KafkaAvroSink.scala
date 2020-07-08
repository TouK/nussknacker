package pl.touk.nussknacker.engine.avro.sink

import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.generic.GenericContainer
import org.apache.flink.streaming.api.datastream.DataStreamSink
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.nussknacker.engine.api.{InterpretationResult, LazyParameter}
import pl.touk.nussknacker.engine.avro.KafkaAvroSchemaProvider
import pl.touk.nussknacker.engine.avro.encode.BestEffortAvroEncoder
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSink}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PartitionByKeyFlinkKafkaProducer, PreparedKafkaTopic}

/**
  * TODO: consider putting there avroSchemaString instead of kafkaAvroSchemaProvider. We can't put there schema,
  * because flink on scala 2.11 has problem with serialization it.
  *
  * @param preparedTopic
  * @param output
  * @param kafkaConfig
  * @param kafkaAvroSchemaProvider
  * @param clientId
  */
class KafkaAvroSink(preparedTopic: PreparedKafkaTopic, output: LazyParameter[Any], kafkaConfig: KafkaConfig, kafkaAvroSchemaProvider: KafkaAvroSchemaProvider[_], clientId: String)
  extends FlinkSink with Serializable with LazyLogging {

  import org.apache.flink.streaming.api.scala._

  // We don't want serialize it because of flink serialization..
  @transient final protected lazy val avroEncoder = BestEffortAvroEncoder()

  //It's work around for putting schema by field in class, because flink on scala 2.11 has problem with serialization Schema..
  @transient final protected lazy val schema = kafkaAvroSchemaProvider.fetchTopicValueSchema.valueOr(throw _)

  override def registerSink(dataStream: DataStream[InterpretationResult], flinkNodeContext: FlinkCustomNodeContext): DataStreamSink[_] = {
    dataStream
      .map(_.finalContext)
      .map(flinkNodeContext.lazyParameterHelper.lazyMapFunction(output))
      .map(ctx => ctx.value match {
          case data: java.util.Map[String@unchecked, Any@unchecked] => avroEncoder.encodeRecordOrError(data, schema)
          case _: GenericContainer => ctx.value
          case _ => {
            //TODO: We should better handle this situation by using EspExceptionHandler
            logger.error(s"Invalid output type error for topic: ${preparedTopic.prepared}.", ctx)
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
    PartitionByKeyFlinkKafkaProducer(kafkaConfig, preparedTopic.prepared, kafkaAvroSchemaProvider.serializationSchema, clientId)
}
