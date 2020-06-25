package pl.touk.nussknacker.engine.avro.sink

import org.apache.avro.Schema
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory}
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData}
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.avro.{KafkaAvroFactory, KafkaAvroSchemaProvider}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSink
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils}

abstract class BaseKafkaAvroSinkFactory(processObjectDependencies: ProcessObjectDependencies) extends SinkFactory {

  override def requiresOutput: Boolean = false

  // We currently not using nodeId but it is here in case if someone want to use in their own concrete implementation
  protected def createSink(topic: String,
                           output: LazyParameter[Any],
                           kafkaConfig: KafkaConfig,
                           kafkaAvroSchemaProvider: KafkaAvroSchemaProvider[_],
                           processMetaData: MetaData,
                           nodeId: NodeId): FlinkSink = {

    val schema = kafkaAvroSchemaProvider.fetchTopicValueSchema.valueOr(KafkaAvroFactory.handleError)

    validateOutput(output, schema)

    val preparedTopic = KafkaUtils.prepareTopicName(topic, processObjectDependencies)
    val serializationSchema = kafkaAvroSchemaProvider.serializationSchema
    val clientId = s"${processMetaData.id}-$preparedTopic"

    new KafkaAvroSink(topic, output, kafkaConfig, kafkaAvroSchemaProvider, clientId)
  }

  /**
    * Currently we check only required fields, because our typing mechanism doesn't support optionally fields
    *
    * @param output
    * @param schema
    */
  protected def validateOutput(output: LazyParameter[Any], schema: Schema): Unit = {
    val possibleTypes = AvroSchemaTypeDefinitionExtractor.ExtendedPossibleTypes
    val returnType = AvroSchemaTypeDefinitionExtractor.typeDefinitionWithoutNullableFields(schema, possibleTypes)

    if (!output.returnType.canBeSubclassOf(returnType)) {
      KafkaAvroFactory.handleError(InvalidSinkOutput("Provided output doesn't match to selected avro schema."))
    }
  }
}
