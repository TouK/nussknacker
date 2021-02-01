package pl.touk.nussknacker.engine.avro.sink

import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.generic.GenericContainer
import org.apache.flink.api.common.functions.{RichMapFunction, RuntimeContext}
import org.apache.flink.formats.avro.typeutils.NkSerializableAvroSchema
import org.apache.flink.streaming.api.datastream.DataStreamSink
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.nussknacker.engine.api.{InterpretationResult, LazyParameter, ValueWithContext}
import pl.touk.nussknacker.engine.avro.encode.{BestEffortAvroEncoder, ValidationMode}
import pl.touk.nussknacker.engine.avro.schemaregistry.{ExistingSchemaVersion, SchemaVersionOption}
import pl.touk.nussknacker.engine.avro.serialization.KafkaAvroSerializationSchemaFactory
import pl.touk.nussknacker.engine.avro.sink.KafkaAvroSink.KeyedObjectMapper
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionHandler, WithFlinkEspExceptionHandler}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSink}
import pl.touk.nussknacker.engine.flink.util.keyed.{KeyedValue, KeyedValueMapper}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PartitionByKeyFlinkKafkaProducer, PreparedKafkaTopic}

object KafkaAvroSink {

  import com.typesafe.scalalogging.LazyLogging
  import org.apache.flink.api.common.functions.RichFlatMapFunction
  import org.apache.flink.util.Collector
  import pl.touk.nussknacker.engine.api.typed.typing
  import pl.touk.nussknacker.engine.api.{Context, LazyParameter, LazyParameterInterpreter, ValueWithContext}
  import pl.touk.nussknacker.engine.flink.api.process.{FlinkLazyParameterFunctionHelper, LazyParameterInterpreterFunction}
  import pl.touk.nussknacker.engine.flink.util.keyed
  import pl.touk.nussknacker.engine.flink.util.keyed.KeyedValue

  import scala.util.control.NonFatal


  // TODO FIXME: sanitize key!
  class KeyedObjectMapper(protected val lazyParameterHelper: FlinkLazyParameterFunctionHelper,
                          key: LazyParameter[AnyRef],
                          fields: List[(String, LazyParameter[AnyRef])])
    extends RichFlatMapFunction[Context, ValueWithContext[KeyedValue[AnyRef, AnyRef]]]
      with LazyParameterInterpreterFunction with LazyLogging {
    implicit def lazyParameterInterpreterImpl: LazyParameterInterpreter = lazyParameterInterpreter

    lazy val buildObject: LazyParameter[Map[String, AnyRef]] = {
      val emptyObj = Map.empty[String, AnyRef]
      lazyObjectFieldsSequence
        .map { list =>
          list.foldLeft(emptyObj) { case (obj, field) =>
            obj + (field._1 -> field._2)
          }
        }
    }

    override def flatMap(value: Context, out: Collector[ValueWithContext[KeyedValue[AnyRef, AnyRef]]]): Unit = {
      try {
        out.collect(ValueWithContext(interpret(value), value))
      } catch {
        case NonFatal(e) => logger.error(e.getMessage, e)
      }
    }

    private def interpret(ctx: Context): keyed.KeyedValue[AnyRef, AnyRef] =
      lazyParameterInterpreter.syncInterpretationFunction(
        buildObject.map(obj => KeyedValue(key, obj))
      )(ctx)

    private def lazyObjectFieldsSequence: LazyParameter[List[(String, AnyRef)]] = {
      val outType = typing.Typed[List[(String, AnyRef)]]
      val empty = lazyParameterInterpreter.pure[List[(String, AnyRef)]](Nil, outType)
      fields.foldLeft(empty) { case (agg, lazyField) =>
        aggregateLazyParam(agg, lazyField)
      }
    }

    private def aggregateLazyParam(agg: LazyParameter[List[(String, AnyRef)]], lazyField: (String, LazyParameter[AnyRef])): LazyParameter[List[(String, AnyRef)]] = {
      val outType = typing.Typed[List[(String, AnyRef)]]
      agg.product(lazyField._2).map(
        fun = {
          case (list, value) =>
            (lazyField._1, value) :: list
        },
        outputTypingResult = outType
      )
    }
  }
}

class KafkaAvroSink(preparedTopic: PreparedKafkaTopic,
                    versionOption: SchemaVersionOption,
                    key: LazyParameter[AnyRef],
                    valueEither: Either[List[(String, LazyParameter[AnyRef])], LazyParameter[AnyRef]],
                    kafkaConfig: KafkaConfig,
                    serializationSchemaFactory: KafkaAvroSerializationSchemaFactory,
                    schema: NkSerializableAvroSchema,
                    runtimeSchema: Option[NkSerializableAvroSchema],
                    clientId: String,
                    validationMode: ValidationMode)
  extends FlinkSink with Serializable with LazyLogging {

  import org.apache.flink.streaming.api.scala._

  // We don't want serialize it because of flink serialization..
  @transient final protected lazy val avroEncoder = BestEffortAvroEncoder(validationMode)

  override def registerSink(dataStream: DataStream[InterpretationResult], flinkNodeContext: FlinkCustomNodeContext): DataStreamSink[_] = {
    val ds1 = dataStream.map(_.finalContext)
    val ds2 = valueEither match {
      case Right(value) =>
        ds1.map(new KeyedValueMapper(flinkNodeContext.lazyParameterHelper, key, value))
      case Left(fields) =>
        ds1.flatMap(new KeyedObjectMapper(flinkNodeContext.lazyParameterHelper, key, fields))
    }
    ds2
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
