package pl.touk.nussknacker.engine.avro.sink

import org.apache.avro.generic.GenericContainer
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData}
import pl.touk.nussknacker.engine.avro.{KafkaAvroFactory, KafkaAvroSchemaProvider}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils}

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
    //This is only for checking topic subject and version
    val schemaTypeDefinition = kafkaAvroSchemaProvider.returnType(KafkaAvroFactory.handleSchemaRegistryError)

    //TODO: Add more satisfying validation
  }
}
