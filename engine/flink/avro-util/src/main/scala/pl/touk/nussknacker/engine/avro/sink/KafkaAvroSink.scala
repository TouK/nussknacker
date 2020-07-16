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
import pl.touk.nussknacker.engine.flink.util.keyed.StringKeyedValue
import pl.touk.nussknacker.engine.flink.util.transformer.aggregate.StringKeyedValueMapper
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PartitionByKeyFlinkKafkaProducer, PreparedKafkaTopic}

import scala.util.control.NonFatal

class KafkaAvroSink(preparedTopic: PreparedKafkaTopic, version: Option[Int], key: LazyParameter[CharSequence], value: LazyParameter[AnyRef],
                    kafkaConfig: KafkaConfig, serializationSchemaFactory: KafkaAvroSerializationSchemaFactory,
                    schema: NkSerializableAvroSchema, runtimeSchema: Option[NkSerializableAvroSchema], clientId: String)
  extends FlinkSink with Serializable with LazyLogging {

  import org.apache.flink.streaming.api.scala._

  // We don't want serialize it because of flink serialization..
  @transient final protected lazy val avroEncoder = BestEffortAvroEncoder()

  override def registerSink(dataStream: DataStream[InterpretationResult], flinkNodeContext: FlinkCustomNodeContext): DataStreamSink[_] = {
    dataStream
      .map(_.finalContext)
      .map(new StringKeyedValueMapper(flinkNodeContext.lazyParameterHelper, key, value))
      .map(ctx => ctx.value.map {
          case container: GenericContainer => container
          // We try to encode not only Map[String, AnyRef], but also other types because avro accept also primitive types
          case data =>
            try {
              avroEncoder.encodeOrError(data, schema.getAvroSchema)
            } catch {
              case NonFatal(ex) =>
                //TODO: We should better handle this situation by using EspExceptionHandler
                logger.error(s"Invalid value for topic: ${preparedTopic.prepared} and version: $version: $data", ex)
                null
            }
      })
      .filter(_.value != null)
      .addSink(toFlinkFunction)
  }

  /**
    * Right now we don't support it, because we don't use default sink behavior with expression..
    */
  override def testDataOutput: Option[Any => String] = None

  private def toFlinkFunction: SinkFunction[StringKeyedValue[AnyRef]] =
    PartitionByKeyFlinkKafkaProducer(kafkaConfig, preparedTopic.prepared,
      serializationSchemaFactory.create(preparedTopic.prepared, version, runtimeSchema, kafkaConfig), clientId)
}
