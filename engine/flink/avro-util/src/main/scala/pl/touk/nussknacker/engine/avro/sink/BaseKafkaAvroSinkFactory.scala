package pl.touk.nussknacker.engine.avro.sink

import java.nio.charset.StandardCharsets

import org.apache.avro.generic.GenericContainer
import org.apache.flink.streaming.api.datastream.DataStreamSink
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.scala.{DataStream, _}
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.api.typed.typing.TypedObjectTypingResult
import pl.touk.nussknacker.engine.api.{InterpretationResult, LazyParameter, MetaData}
import pl.touk.nussknacker.engine.avro.{KafkaAvroFactory, KafkaAvroSchemaProvider}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSink}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils, PartitionByKeyFlinkKafkaProducer}

abstract class BaseKafkaAvroSinkFactory(processObjectDependencies: ProcessObjectDependencies) extends SinkFactory {

  override def requiresOutput: Boolean = false

  // We currently not using nodeId but it is here in case if someone want to use in their own concrete implementation
  protected def createSink(topic: String,
                           output: LazyParameter[GenericContainer],
                           kafkaConfig: KafkaConfig,
                           kafkaAvroSchemaProvider: KafkaAvroSchemaProvider[_],
                           processMetaData: MetaData,
                           nodeId: NodeId): Sink = {

    validateOutput(output, kafkaAvroSchemaProvider)

    val preparedTopic = KafkaUtils.prepareTopicName(topic, processObjectDependencies)
    val serializationSchema = kafkaAvroSchemaProvider.serializationSchema
    val clientId = s"${processMetaData.id}-$preparedTopic"
    new KafkaAvroSink(topic, output, kafkaConfig, serializationSchema, clientId)
  }

  protected def validateOutput(output: LazyParameter[GenericContainer], kafkaAvroSchemaProvider: KafkaAvroSchemaProvider[_]): Unit = {
    val schemaTypeDefinition = kafkaAvroSchemaProvider.returnType(KafkaAvroFactory.handleSchemaRegistryError)
    val outputTypeDefinition = output.returnType

    val validationResult = outputTypeDefinition match {
      case _: TypedObjectTypingResult => outputTypeDefinition.canBeSubclassOf(schemaTypeDefinition)
      case _ => true //TODO: Add possibility to validate when information about inferred type disappeared
    }

    if (!validationResult) {
      throw CustomNodeValidationException(
        InvalidSinkOutput("Invalid output schema. Please provide correct output."),
        Some(KafkaAvroFactory.SinkOutputParamName)
      )
    }
  }

  //Output should be LazyParameter[GenericContainer]?
  class KafkaAvroSink(topic: String, output: LazyParameter[Any], kafkaConfig: KafkaConfig, serializationSchema: KafkaSerializationSchema[Any], clientId: String)
    extends FlinkSink with Serializable {

    override def registerSink(dataStream: DataStream[InterpretationResult], flinkNodeContext: FlinkCustomNodeContext): DataStreamSink[_] =
      dataStream
        .map(_.finalContext)
        .map(flinkNodeContext.lazyParameterHelper.lazyMapFunction(output))
        .map(_.value)
        .addSink(toFlinkFunction)

    override def testDataOutput: Option[Any => String] = Option(value =>
      new String(serializationSchema.serialize(value, System.currentTimeMillis()).value(), StandardCharsets.UTF_8))

    private def toFlinkFunction: SinkFunction[Any] =
      PartitionByKeyFlinkKafkaProducer(kafkaConfig, topic, serializationSchema, clientId)
  }
}
