package pl.touk.nussknacker.engine.lite.components

import cats.data.NonEmptyList
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.{LazyParameter, NodeId, Params, VariableConstants}
import pl.touk.nussknacker.engine.api.context.transformation.NodeDependencyValue
import pl.touk.nussknacker.engine.api.definition.{Parameter, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.kafka.{KafkaComponentsConfig, PreparedKafkaTopic}
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.kafka.source.KafkaTestParametersInfo
import pl.touk.nussknacker.engine.lite.kafka.api.LiteKafkaSource
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalToJsonFormatter
import pl.touk.nussknacker.engine.schemedkafka.source.KafkaSourceImplFactory
import pl.touk.nussknacker.engine.util.parameters.TestingParametersSupport
import pl.touk.nussknacker.engine.util.watermarkstrategy.WatermarkStrategyOptions

class LiteKafkaSourceImplFactory[K, V] extends KafkaSourceImplFactory[K, V] {

  override def createSource(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Any,
      preparedTopics: NonEmptyList[PreparedKafkaTopic[TopicName.ForSource]],
      kafkaComponentsConfig: KafkaComponentsConfig,
      deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
      formatter: UniversalToJsonFormatter[K, V],
      contextInitializer: ContextInitializer[ConsumerRecord[K, V]],
      testParametersInfo: KafkaTestParametersInfo,
      namingStrategy: NamingStrategy,
      // This options are not handled TODO we should either not present these parameters or add the support for them
      watermarkStrategyOptions: WatermarkStrategyOptions
  ): Source = {
    new LiteKafkaSourceImpl(
      contextInitializer,
      deserializationSchema,
      TypedNodeDependency[NodeId].extract(dependencies),
      preparedTopics,
      kafkaComponentsConfig,
      formatter,
      testParametersInfo
    )
  }

}

class LiteKafkaSourceImpl[K, V](
    contextInitializer: ContextInitializer[ConsumerRecord[K, V]],
    deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
    val nodeId: NodeId,
    preparedTopics: NonEmptyList[PreparedKafkaTopic[TopicName.ForSource]],
    val kafkaComponentsConfig: KafkaComponentsConfig,
    val formatter: UniversalToJsonFormatter[K, V],
    testParametersInfo: KafkaTestParametersInfo
) extends LiteKafkaSource
    with SourceTestSupport[ConsumerRecord[Array[Byte], Array[Byte]]]
    with TestDataGenerator
    with TestWithParametersSupport[ConsumerRecord[Array[Byte], Array[Byte]]] {

  override lazy val topics: NonEmptyList[TopicName.ForSource] = preparedTopics.map(_.prepared)

  override def transform(record: ConsumerRecord[Array[Byte], Array[Byte]]): ContextVariables = {
    val deserialized = deserializationSchema.deserialize(record)
    // TODO: what about other properties based on kafkaComponentsConfig?
    val initializerVariables = contextInitializer.convertToInitialVariables(deserialized)
    ContextVariables(
      initializerVariables.variables + (VariableConstants.EventTimestampVariableName -> record.timestamp())
    )
  }

  override def generateTestData(size: Int): TestData = formatter.generateTestData(topics, size, kafkaComponentsConfig)

  // We don't use passed deserializationSchema, as in lite tests deserialization is done after parsing test data
  // (see difference with Flink implementation)
  override def testRecordParser: TestRecordParser[ConsumerRecord[Array[Byte], Array[Byte]]] =
    (testRecords: List[TestRecord]) => testRecords.map { testRecord => formatter.parseRecord(topics.head, testRecord) }

  override def testParametersDefinition: List[Parameter] = testParametersInfo.parametersDefinition

  override def parametersToTestData(params: Map[ParameterName, AnyRef]): ConsumerRecord[Array[Byte], Array[Byte]] = {
    val unflattenedParams = TestingParametersSupport.unflattenParameters(params)
    val removedValue = if (unflattenedParams.size == 1) {
      unflattenedParams.head match {
        case ("Value", inner) => inner
        case _                => unflattenedParams
      }
    } else unflattenedParams
    formatter.parseRecord(topics.head, testParametersInfo.createTestRecord(removedValue))
  }

}
