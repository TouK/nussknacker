package pl.touk.nussknacker.engine.lite.components

import io.circe.Json
import io.circe.Json.Null
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.json.JSONObject
import pl.touk.nussknacker.engine.api.{CirceUtil, Context, NodeId, VariableConstants}
import pl.touk.nussknacker.engine.api.context.transformation.NodeDependencyValue
import pl.touk.nussknacker.engine.api.definition.{Parameter, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.process.{TestWithParametersSupport, _}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.test.{TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.json.JsonSinkValueParameter
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.KafkaSourceImplFactory
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic, RecordFormatter, RecordFormatterBaseTestDataGenerator}
import pl.touk.nussknacker.engine.lite.kafka.api.LiteKafkaSource
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

class LiteKafkaSourceImplFactory[K, V] extends KafkaSourceImplFactory[K, V] {

  override def createSource(params: Map[String, Any],
                            dependencies: List[NodeDependencyValue],
                            finalState: Any,
                            preparedTopics: List[PreparedKafkaTopic],
                            kafkaConfig: KafkaConfig,
                            deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
                            formatter: RecordFormatter,
                            contextInitializer: ContextInitializer[ConsumerRecord[K, V]],
                            uiParameters: List[Parameter] = Nil): Source = {
    new LiteKafkaSourceImpl(contextInitializer, deserializationSchema, TypedNodeDependency[NodeId].extract(dependencies), preparedTopics, kafkaConfig, formatter, uiParameters)
  }

}

class LiteKafkaSourceImpl[K, V](contextInitializer: ContextInitializer[ConsumerRecord[K, V]],
                                deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
                                val nodeId: NodeId,
                                preparedTopics: List[PreparedKafkaTopic],
                                val kafkaConfig: KafkaConfig,
                                val formatter: RecordFormatter,
                                uiParameters: List[Parameter]) extends LiteKafkaSource with
  SourceTestSupport[ConsumerRecord[Array[Byte], Array[Byte]]] with RecordFormatterBaseTestDataGenerator with TestWithParametersSupport[ConsumerRecord[Array[Byte], Array[Byte]]] {

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
  }

  //We don't use passed deserializationSchema, as in lite tests deserialization is done after parsing test data
  //(see difference with Flink implementation)
  override def testRecordParser: TestRecordParser[ConsumerRecord[Array[Byte], Array[Byte]]] = (testRecord: TestRecord) =>
    formatter.parseRecord(topics.head, testRecord)

  override def testParametersDefinition: List[Parameter] = uiParameters

  private def testKafkaRecordWrapper(testData: Json) = {
    BestEffortJsonEncoder.defaultForTests.encode(Map(
      "keySchemaId" -> null,
      "valueSchemaId" -> null,
      "consumerRecord" -> Map(
        "value" -> testData,
        "topic" -> topics.head,
        "partition" -> 0,
        "offset" -> 269616234,
        "timestamp" -> 1684338134162L,
        "timestampType" -> "CreateTime",
        "headers" -> Map[String, String](),
        "leaderEpoch" -> 47
      )))
  }

  override def parametersToTestData(params: Map[String, AnyRef]): ConsumerRecord[Array[Byte], Array[Byte]] = {
    val json = BestEffortJsonEncoder.defaultForTests.encode(JsonSinkValueParameter.unflattenParameters(params))
    val uJson = testKafkaRecordWrapper(json)
    println(uJson)
    formatter.parseRecord(topics.head, TestRecord(uJson))
  }
}
