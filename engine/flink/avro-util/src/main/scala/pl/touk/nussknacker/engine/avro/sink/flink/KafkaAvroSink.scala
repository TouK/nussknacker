package pl.touk.nussknacker.engine.avro.sink.flink

import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.generic.GenericContainer
import org.apache.flink.api.common.functions.{RichMapFunction, RuntimeContext}
import org.apache.flink.formats.avro.typeutils.NkSerializableAvroSchema
import org.apache.flink.streaming.api.datastream.DataStreamSink
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, ValueWithContext}
import pl.touk.nussknacker.engine.avro.encode.{BestEffortAvroEncoder, ValidationMode}
import pl.touk.nussknacker.engine.avro.schemaregistry.{ExistingSchemaVersion, SchemaVersionOption}
import pl.touk.nussknacker.engine.avro.serialization.KafkaAvroSerializationSchemaFactory
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionHandler, WithFlinkEspExceptionHandler}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSink}
import pl.touk.nussknacker.engine.flink.util.keyed.KeyedValueMapper
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PartitionByKeyFlinkKafkaProducer, PreparedKafkaTopic}
import pl.touk.nussknacker.engine.util.KeyedValue

class KafkaAvroSink(preparedTopic: PreparedKafkaTopic,
                    versionOption: SchemaVersionOption,
                    key: LazyParameter[AnyRef],
                    sinkValue: AvroSinkValue,
                    kafkaConfig: KafkaConfig,
                    serializationSchemaFactory: KafkaAvroSerializationSchemaFactory[KeyedValue[AnyRef, AnyRef]],
                    schema: NkSerializableAvroSchema,
                    runtimeSchema: Option[NkSerializableAvroSchema],
                    clientId: String,
                    validationMode: ValidationMode)
  extends FlinkSink with Serializable with LazyLogging {

  import org.apache.flink.streaming.api.scala._

  type Value = KeyedValue[AnyRef, AnyRef]

  // We don't want serialize it because of flink serialization..
  @transient final protected lazy val avroEncoder = BestEffortAvroEncoder(validationMode)

  override def registerSink(dataStream: DataStream[ValueWithContext[Value]], flinkNodeContext: FlinkCustomNodeContext): DataStreamSink[_] =
    dataStream
      .map(new EncodeAvroRecordFunction(flinkNodeContext))
      .filter(_.value != null)
      .addSink(toFlinkFunction)

  def prepareValue(ds: DataStream[Context], flinkNodeContext: FlinkCustomNodeContext): DataStream[ValueWithContext[Value]] =
    sinkValue match {
      case AvroSinkSingleValue(value, _) =>
        ds.flatMap(new KeyedValueMapper(flinkNodeContext.lazyParameterHelper, key, value))
      case sinkRecord: AvroSinkRecordValue =>
        ds.flatMap(KeyedRecordFlatMapper(flinkNodeContext, key, sinkRecord))
    }

  private def toFlinkFunction: SinkFunction[KeyedValue[AnyRef, AnyRef]] = {
    val versionOpt = Option(versionOption).collect {
      case ExistingSchemaVersion(version) => version
    }
    PartitionByKeyFlinkKafkaProducer(kafkaConfig, preparedTopic.prepared,
      serializationSchemaFactory.create(preparedTopic.prepared, versionOpt, runtimeSchema, kafkaConfig), clientId)
  }

  class EncodeAvroRecordFunction(flinkNodeContext: FlinkCustomNodeContext)
    extends RichMapFunction[ValueWithContext[KeyedValue[AnyRef, AnyRef]], KeyedValue[AnyRef, AnyRef]]
      with WithFlinkEspExceptionHandler {

    private val nodeId = flinkNodeContext.nodeId

    protected override val exceptionHandlerPreparer: RuntimeContext => FlinkEspExceptionHandler = flinkNodeContext.exceptionHandlerPreparer

    override def map(ctx: ValueWithContext[KeyedValue[AnyRef, AnyRef]]): KeyedValue[AnyRef, AnyRef] = {
      ctx.value.mapValue {
        case container: GenericContainer => container
        // We try to encode not only Map[String, AnyRef], but also other types because avro accept also primitive types
        case data =>
          exceptionHandler.handling(Some(nodeId), ctx.context) {
            avroEncoder.encodeOrError(data, schema.getAvroSchema)
          }.orNull
      }
    }
  }
}
