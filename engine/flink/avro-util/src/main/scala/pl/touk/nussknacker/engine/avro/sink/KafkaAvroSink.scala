package pl.touk.nussknacker.engine.avro.sink

import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.generic.GenericContainer
import org.apache.flink.formats.avro.typeutils.NkSerializableAvroSchema
import org.apache.flink.streaming.api.datastream.DataStreamSink
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.nussknacker.engine.api.{InterpretationResult, LazyParameter}
import pl.touk.nussknacker.engine.avro.encode.BestEffortAvroEncoder
import pl.touk.nussknacker.engine.avro.serialization.KafkaAvroSerializationSchemaFactory
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSink}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PartitionByKeyFlinkKafkaProducer, PreparedKafkaTopic}

class KafkaAvroSink(preparedTopic: PreparedKafkaTopic, version: Option[Int], output: LazyParameter[AnyRef],
                    kafkaConfig: KafkaConfig, serializationSchemaFactory: KafkaAvroSerializationSchemaFactory,
                    schema: NkSerializableAvroSchema, runtimeSchema: Option[NkSerializableAvroSchema], clientId: String)
  extends FlinkSink with Serializable with LazyLogging {

  import org.apache.flink.streaming.api.scala._

  // We don't want serialize it because of flink serialization..
  @transient final protected lazy val avroEncoder = BestEffortAvroEncoder()

  override def registerSink(dataStream: DataStream[InterpretationResult], flinkNodeContext: FlinkCustomNodeContext): DataStreamSink[_] = {
    dataStream
      .map(_.finalContext)
      .map(flinkNodeContext.lazyParameterHelper.lazyMapFunction(output))
      .map(ctx => ctx.value match {
          case data: java.util.Map[String@unchecked, Any@unchecked] => avroEncoder.encodeRecordOrError(data, schema.getAvroSchema)
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

  private def toFlinkFunction: SinkFunction[AnyRef] =
    PartitionByKeyFlinkKafkaProducer(kafkaConfig, preparedTopic.prepared,
      serializationSchemaFactory.create(preparedTopic.prepared, version, runtimeSchema, kafkaConfig), clientId)
}
