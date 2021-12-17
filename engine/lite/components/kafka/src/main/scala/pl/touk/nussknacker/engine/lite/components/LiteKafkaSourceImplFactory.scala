package pl.touk.nussknacker.engine.lite.components

import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.{Context, VariableConstants}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.transformation.NodeDependencyValue
import pl.touk.nussknacker.engine.api.definition.TypedNodeDependency
import pl.touk.nussknacker.engine.api.deployment.TestProcess.TestData
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.test.TestDataParser
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.KafkaSourceImplFactory
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic, RecordFormatter, RecordFormatterBaseTestDataGenerator}
import pl.touk.nussknacker.engine.lite.kafka.api.LiteKafkaSource

class LiteKafkaSourceImplFactory[K, V] extends KafkaSourceImplFactory[K, V] {

  override def createSource(params: Map[String, Any],
                            dependencies: List[NodeDependencyValue],
                            finalState: Any,
                            preparedTopics: List[PreparedKafkaTopic],
                            kafkaConfig: KafkaConfig,
                            deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
                            formatter: RecordFormatter,
                            contextInitializer: ContextInitializer[ConsumerRecord[K, V]]): Source = {
    new LiteKafkaSourceImpl(contextInitializer, deserializationSchema, TypedNodeDependency[NodeId].extract(dependencies), preparedTopics, kafkaConfig, formatter)
  }

}

class LiteKafkaSourceImpl[K, V](contextInitializer: ContextInitializer[ConsumerRecord[K, V]],
                                deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
                                val nodeId: NodeId,
                                preparedTopics: List[PreparedKafkaTopic],
                                val kafkaConfig: KafkaConfig,
                                val formatter: RecordFormatter) extends LiteKafkaSource with SourceTestSupport[ConsumerRecord[Array[Byte], Array[Byte]]] with RecordFormatterBaseTestDataGenerator {

  private var initializerFun: ContextInitializingFunction[ConsumerRecord[K, V]] = _

  override def open(context: EngineRuntimeContext): Unit = {
    super.open(context)
    initializerFun = contextInitializer.initContext(contextIdGenerator)
  }

  override val topics: List[String] = preparedTopics.map(_.prepared)

  override def transform(record: ConsumerRecord[Array[Byte], Array[Byte]]): Context = {
    val deserialized = deserializationSchema.deserialize(record)
    // TODO: what about other properties based on kafkaConfig?
    initializerFun(deserialized)
      .withVariable(VariableConstants.EventTimestampVariableName, record.timestamp())
      //super.addVariableToContext(initializerFun(deserialized), VariableConstants.EventTimestampVariableName, record.timestamp())
  }

  //We don't use passed deserializationSchema, as in lite tests deserialization is done after parsing test data
  //(see difference with Flink implementation)
  override def testDataParser: TestDataParser[ConsumerRecord[Array[Byte], Array[Byte]]] = (merged: TestData) =>
    formatter.parseDataForTest(topics, merged.testData)


}