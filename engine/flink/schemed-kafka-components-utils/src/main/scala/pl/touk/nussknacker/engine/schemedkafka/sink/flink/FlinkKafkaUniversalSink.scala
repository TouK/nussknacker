package pl.touk.nussknacker.engine.schemedkafka.sink.flink

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.flink.api.common.functions.{RichMapFunction, RuntimeContext}
import org.apache.flink.formats.avro.typeutils.NkSerializableParsedSchema
import org.apache.flink.streaming.api.datastream.DataStreamSink
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.nussknacker.engine.api.component.{ComponentType, NodeComponentInfo}
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.exception.{ExceptionHandler, WithExceptionHandler}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSink}
import pl.touk.nussknacker.engine.flink.util.keyed.KeyedValueMapper
import pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchema
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PartitionByKeyFlinkKafkaProducer, PreparedKafkaTopic}
import pl.touk.nussknacker.engine.schemedkafka.encode.ValidationMode
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.UniversalSchemaSupport
import pl.touk.nussknacker.engine.util.KeyedValue

class FlinkKafkaUniversalSink(preparedTopic: PreparedKafkaTopic,
                              key: LazyParameter[AnyRef],
                              value: LazyParameter[AnyRef],
                              kafkaConfig: KafkaConfig,
                              serializationSchema: KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]],
                              clientId: String,
                              schema: NkSerializableParsedSchema[ParsedSchema],
                              validationMode: ValidationMode)
  extends FlinkSink with Serializable with LazyLogging {

  import org.apache.flink.streaming.api.scala._

  type Value = KeyedValue[AnyRef, AnyRef]

  override def registerSink(dataStream: DataStream[ValueWithContext[Value]], flinkNodeContext: FlinkCustomNodeContext): DataStreamSink[_] =
    dataStream
      .map(new EncodeAvroRecordFunction(flinkNodeContext))
      .filter(_.value != null)
      .addSink(toFlinkFunction)

  def prepareValue(ds: DataStream[Context], flinkNodeContext: FlinkCustomNodeContext): DataStream[ValueWithContext[Value]] =
    ds.flatMap(new KeyedValueMapper(flinkNodeContext.lazyParameterHelper, key, value))

  private def toFlinkFunction: SinkFunction[KeyedValue[AnyRef, AnyRef]] = {
    PartitionByKeyFlinkKafkaProducer(kafkaConfig, preparedTopic.prepared, serializationSchema, clientId)
  }

  class EncodeAvroRecordFunction(flinkNodeContext: FlinkCustomNodeContext)
    extends RichMapFunction[ValueWithContext[KeyedValue[AnyRef, AnyRef]], KeyedValue[AnyRef, AnyRef]]
      with WithExceptionHandler {

    private val nodeId = flinkNodeContext.nodeId

    protected override val exceptionHandlerPreparer: RuntimeContext => ExceptionHandler = flinkNodeContext.exceptionHandlerPreparer

    override def map(ctx: ValueWithContext[KeyedValue[AnyRef, AnyRef]]): KeyedValue[AnyRef, AnyRef] = {
      ctx.value.mapValue { data =>
        exceptionHandler.handling(Some(NodeComponentInfo(nodeId, "flinkKafkaAvroSink", ComponentType.Sink)), ctx.context) {
          val encode = UniversalSchemaSupport.forSchemaType(schema.getParsedSchema.schemaType()).sinkValueEncoder(schema.getParsedSchema, validationMode)
          encode(data)
        }.orNull
      }
    }
  }
}
