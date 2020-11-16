package pl.touk.nussknacker.engine.avro.sink

import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.generic.GenericContainer
import org.apache.flink.api.common.functions.{MapFunction, RichMapFunction}
import org.apache.flink.formats.avro.typeutils.NkSerializableAvroSchema
import org.apache.flink.streaming.api.datastream.DataStreamSink
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, NonTransientException}
import pl.touk.nussknacker.engine.api.{InterpretationResult, LazyParameter, ValueWithContext}
import pl.touk.nussknacker.engine.avro.encode.{BestEffortAvroEncoder, ValidationMode}
import pl.touk.nussknacker.engine.avro.schemaregistry.{ExistingSchemaVersion, SchemaVersionOption}
import pl.touk.nussknacker.engine.avro.serialization.KafkaAvroSerializationSchemaFactory
import pl.touk.nussknacker.engine.flink.api.process.{AbstractLazyParameterInterpreterFunction, AbstractOneParamLazyParameterFunction, FlinkCustomNodeContext, FlinkSink, LazyParameterInterpreterFunction}
import pl.touk.nussknacker.engine.flink.util.keyed.{KeyedValue, KeyedValueMapper}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PartitionByKeyFlinkKafkaProducer, PreparedKafkaTopic}

import scala.util.control.NonFatal

class KafkaAvroSink(preparedTopic: PreparedKafkaTopic, versionOption: SchemaVersionOption, key: LazyParameter[AnyRef], value: LazyParameter[AnyRef],
                    kafkaConfig: KafkaConfig, serializationSchemaFactory: KafkaAvroSerializationSchemaFactory,
                    schema: NkSerializableAvroSchema, runtimeSchema: Option[NkSerializableAvroSchema],
                    clientId: String, validationMode: ValidationMode)
  extends FlinkSink with Serializable with LazyLogging {

  import org.apache.flink.streaming.api.scala._

  // We don't want serialize it because of flink serialization..
  @transient final protected lazy val avroEncoder = BestEffortAvroEncoder(validationMode)

  override def registerSink(dataStream: DataStream[InterpretationResult], flinkNodeContext: FlinkCustomNodeContext): DataStreamSink[_] = {
    dataStream
      .map(_.finalContext)
      .map(new KeyedValueMapper(flinkNodeContext.lazyParameterHelper, key, value))
      .map(new EncodeAvroRecordFunction(flinkNodeContext))
      .filter(_.value != null)
      .addSink(toFlinkFunction)
  }

  /**
   * Right now we support it incorrectly, because we don't use default sink behavior with expression..
   */
  override def testDataOutput: Option[Any => String] = Some(value => Option(value).map(_.toString).getOrElse(""))

  private def toFlinkFunction: SinkFunction[KeyedValue[AnyRef, AnyRef]] = {
    val versionOpt = Option(versionOption).collect {
      case ExistingSchemaVersion(version) => version
    }
    PartitionByKeyFlinkKafkaProducer(kafkaConfig, preparedTopic.prepared,
      serializationSchemaFactory.create(preparedTopic.prepared, versionOpt, runtimeSchema, kafkaConfig), clientId)
  }

  class EncodeAvroRecordFunction(flinkNodeContext: FlinkCustomNodeContext)
    extends AbstractLazyParameterInterpreterFunction(flinkNodeContext.lazyParameterHelper)
      with MapFunction[ValueWithContext[KeyedValue[AnyRef, AnyRef]], KeyedValue[AnyRef, AnyRef]] {

    @transient final val nodeId = flinkNodeContext.nodeId

    override def map(ctx: ValueWithContext[KeyedValue[AnyRef, AnyRef]]): KeyedValue[AnyRef, AnyRef] = {
      ctx.value.mapValue {
        case container: GenericContainer => container
        // We try to encode not only Map[String, AnyRef], but also other types because avro accept also primitive types
        case data =>
          lazyParameterInterpreter.exceptionHandler.handling(Some(nodeId), ctx.context) {
            avroEncoder.encodeOrError(data, schema.getAvroSchema)
          }.orNull
      }
    }
  }
}
