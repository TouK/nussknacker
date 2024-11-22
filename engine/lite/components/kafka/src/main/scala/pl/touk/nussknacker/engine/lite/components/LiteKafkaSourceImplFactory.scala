package pl.touk.nussknacker.engine.lite.components

import cats.data.NonEmptyList
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.context.transformation.NodeDependencyValue
import pl.touk.nussknacker.engine.api.definition.{Parameter, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.test.{TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.{Context, NodeId, Params, VariableConstants}
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.{KafkaSourceImplFactory, KafkaTestParametersInfo}
import pl.touk.nussknacker.engine.kafka.{
  KafkaConfig,
  PreparedKafkaTopic,
  RecordFormatter,
  RecordFormatterBaseTestDataGenerator
}
import pl.touk.nussknacker.engine.lite.kafka.api.LiteKafkaSource
import pl.touk.nussknacker.engine.util.parameters.TestingParametersSupport

class LiteKafkaSourceImplFactory[K, V] extends KafkaSourceImplFactory[K, V] {

  override def createSource(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Any,
      preparedTopics: NonEmptyList[PreparedKafkaTopic[TopicName.ForSource]],
      kafkaConfig: KafkaConfig,
      deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
      formatter: RecordFormatter,
      contextInitializer: ContextInitializer[ConsumerRecord[K, V]],
      testParametersInfo: KafkaTestParametersInfo,
      namingStrategy: NamingStrategy
  ): Source = {
    new LiteKafkaSourceImpl(
      contextInitializer,
      deserializationSchema,
      TypedNodeDependency[NodeId].extract(dependencies),
      preparedTopics,
      kafkaConfig,
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
    val kafkaConfig: KafkaConfig,
    val formatter: RecordFormatter,
    testParametersInfo: KafkaTestParametersInfo
) extends LiteKafkaSource
    with SourceTestSupport[ConsumerRecord[Array[Byte], Array[Byte]]]
    with RecordFormatterBaseTestDataGenerator
    with TestWithParametersSupport[ConsumerRecord[Array[Byte], Array[Byte]]] {

  private var initializerFun: ContextInitializingFunction[ConsumerRecord[K, V]] = _

  override def open(context: EngineRuntimeContext): Unit = {
    super.open(context)
    initializerFun = contextInitializer.initContext(contextIdGenerator)
  }

  override lazy val topics: NonEmptyList[TopicName.ForSource] = preparedTopics.map(_.prepared)

  override def transform(record: ConsumerRecord[Array[Byte], Array[Byte]]): Context = {
    val deserialized = deserializationSchema.deserialize(record)
    // TODO: what about other properties based on kafkaConfig?
    initializerFun(deserialized)
      .withVariable(VariableConstants.EventTimestampVariableName, record.timestamp())
  }

  // We don't use passed deserializationSchema, as in lite tests deserialization is done after parsing test data
  // (see difference with Flink implementation)
  override def testRecordParser: TestRecordParser[ConsumerRecord[Array[Byte], Array[Byte]]] =
    (testRecords: List[TestRecord]) => testRecords.map { testRecord => formatter.parseRecord(topics.head, testRecord) }

  override def testParametersDefinition: List[Parameter] = testParametersInfo.parametersDefinition

  override def parametersToTestData(params: Map[ParameterName, AnyRef]): ConsumerRecord[Array[Byte], Array[Byte]] = {
    val unflattenedParams = TestingParametersSupport.unflattenParameters(params)
    val removedValue = if (unflattenedParams.size == 1) {
      unflattenedParams.headOption match {
        case Some(("Value", inner)) => inner
        case _                      => unflattenedParams
      }
    } else unflattenedParams
    formatter.parseRecord(topics.head, testParametersInfo.createTestRecord(removedValue))
  }

}
