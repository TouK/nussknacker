package pl.touk.nussknacker.engine.lite.components

import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.transformation.NodeDependencyValue
import pl.touk.nussknacker.engine.api.definition.TypedNodeDependency
import pl.touk.nussknacker.engine.api.process.{ContextInitializer, ContextInitializingFunction, Source}
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.api.{Context, ContextId, Lifecycle}
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.KafkaSourceImplFactory
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic, RecordFormatter}
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
    new LiteKafkaSourceImpl(contextInitializer, deserializationSchema, TypedNodeDependency[NodeId].extract(dependencies), preparedTopics)
  }

}

class LiteKafkaSourceImpl[K, V](contextInitializer: ContextInitializer[ConsumerRecord[K, V]],
                                deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
                                nodeId: NodeId, preparedTopics: List[PreparedKafkaTopic]) extends LiteKafkaSource with Lifecycle {

  private var contextIdGenerator: ContextIdGenerator = _
  private var initializerFun: ContextInitializingFunction[ConsumerRecord[K, V]] = _

  override def open(context: EngineRuntimeContext): Unit = {
    super.open(context)
    contextIdGenerator = context.contextIdGenerator(nodeId.id)
    initializerFun = contextInitializer.initContext(contextIdGenerator)
  }

  override protected def nextContextId: ContextId = ContextId(contextIdGenerator.nextContextId())

  override val topics: List[String] = preparedTopics.map(_.prepared)

  override def transform(record: ConsumerRecord[Array[Byte], Array[Byte]]): Context = {
    val deserialized = deserializationSchema.deserialize(record)
    // TODO: what about other properties based on kafkaConfig?
    initializerFun(deserialized)
  }

}