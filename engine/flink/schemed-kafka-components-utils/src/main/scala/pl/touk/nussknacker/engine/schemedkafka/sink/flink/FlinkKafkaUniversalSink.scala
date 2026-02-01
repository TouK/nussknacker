package pl.touk.nussknacker.engine.schemedkafka.sink.flink

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.flink.api.common.functions.{OpenContext, RichMapFunction}
import org.apache.flink.api.common.typeinfo.{TypeInformation, Types}
import org.apache.flink.api.connector.sink2.Sink
import org.apache.flink.formats.avro.typeutils.NkSerializableParsedSchema
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSink}
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, ValueWithContext}
import pl.touk.nussknacker.engine.api.component.{ComponentType, NodeComponentInfo}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.flink.api.FlinkEngineContextOps._
import pl.touk.nussknacker.engine.flink.api.exception.ExceptionHandler
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSink}
import pl.touk.nussknacker.engine.flink.typeinformation.KeyedValueType
import pl.touk.nussknacker.engine.flink.util.keyed
import pl.touk.nussknacker.engine.flink.util.keyed.{KeyedValueMapper, KeyOptions}
import pl.touk.nussknacker.engine.kafka.{KafkaComponentsConfig, PartitionByKeyFlinkKafkaSink}
import pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchema
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaSupportDispatcher
import pl.touk.nussknacker.engine.util.KeyedValue

class FlinkKafkaUniversalSink(
    key: LazyParameter[AnyRef],
    keyParameterName: ParameterName,
    value: LazyParameter[AnyRef],
    kafkaComponentsConfig: KafkaComponentsConfig,
    serializationSchema: KafkaSerializationSchema[KeyedValue[AnyRef, AnyRef]],
    clientId: String,
    schema: NkSerializableParsedSchema[ParsedSchema],
    validationMode: ValidationMode
) extends FlinkSink
    with Serializable
    with LazyLogging {

  type Value = KeyedValue[AnyRef, AnyRef]

  private lazy val schemaSupportDispatcher = UniversalSchemaSupportDispatcher(kafkaComponentsConfig)

  override def registerSink(
      dataStream: DataStream[ValueWithContext[Value]],
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStreamSink[_] = {

    // TODO: Creating TypeInformation for Avro / Json Schema is difficult because of schema evolution, therefore we rely on Kryo, e.g. serializer for GenericRecordWithSchemaId
    val typeInfo = KeyedValueType
      .info(
        Types.STRING, // KafkaSink for key supports only String
        Types.GENERIC(classOf[AnyRef])
      )
      .asInstanceOf[TypeInformation[KeyedValue[AnyRef, AnyRef]]]

    dataStream
      .map(new EncodeAvroRecordFunction(flinkNodeContext), typeInfo)
      .filter(_.value != null)
      .sinkTo(toFlinkSink(flinkNodeContext))
  }

  def prepareValue(
      ds: DataStream[Context],
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[ValueWithContext[Value]] = {
    val typeInfo = keyed.typeInfo(flinkNodeContext, key, value)
    ds.flatMap(
      new KeyedValueMapper(
        flinkNodeContext.lazyParameterHelper,
        key,
        keyParameterName,
        KeyOptions(allowNullableKeys = true),
        value
      ),
      typeInfo
    )
  }

  private def toFlinkSink(flinkNodeContext: FlinkCustomNodeContext): Sink[KeyedValue[AnyRef, AnyRef]] = {
    PartitionByKeyFlinkKafkaSink(kafkaComponentsConfig, serializationSchema, clientId, flinkNodeContext.nodeId)
  }

  class EncodeAvroRecordFunction(flinkNodeContext: FlinkCustomNodeContext)
      extends RichMapFunction[ValueWithContext[KeyedValue[AnyRef, AnyRef]], KeyedValue[AnyRef, AnyRef]] {

    @transient private var exceptionHandler: ExceptionHandler = _

    @transient private var encodeRecord: Any => AnyRef = _

    override def open(openContext: OpenContext): Unit = {
      super.open(openContext)
      encodeRecord = schemaSupportDispatcher
        .forParsedSchema(schema.getParsedSchema)
        .formValueEncoder(schema.getParsedSchema, validationMode)
      exceptionHandler = flinkNodeContext.exceptionHandlerPreparer.narrowToRuntimeCtx(getRuntimeContext)
    }

    override def map(ctx: ValueWithContext[KeyedValue[AnyRef, AnyRef]]): KeyedValue[AnyRef, AnyRef] = {
      ctx.value.mapValue { data =>
        exceptionHandler
          .handling(
            Some(NodeComponentInfo(flinkNodeContext.nodeId, ComponentType.Sink, "flinkKafkaUniversalSink")),
            ctx.context
          ) {
            encodeRecord(data)
          }
          .orNull
      }
    }

    override def close(): Unit = {
      if (exceptionHandler != null) {
        exceptionHandler.close()
      }
      super.close()
    }

  }

}
