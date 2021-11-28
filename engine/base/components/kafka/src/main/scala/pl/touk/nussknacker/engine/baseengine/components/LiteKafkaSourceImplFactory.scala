package pl.touk.nussknacker.engine.baseengine.components

import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.transformation.NodeDependencyValue
import pl.touk.nussknacker.engine.api.definition.TypedNodeDependency
import pl.touk.nussknacker.engine.api.process.{ContextInitializer, Source}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.{Context, Lifecycle}
import pl.touk.nussknacker.engine.baseengine.kafka.api.LiteKafkaSource
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.KafkaSourceImplFactory
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic, RecordFormatter}

class LiteKafkaSourceImplFactory[K, V] extends KafkaSourceImplFactory[K, V] {

  override def createSource(params: Map[String, Any],
                            dependencies: List[NodeDependencyValue],
                            finalState: Any,
                            preparedTopics: List[PreparedKafkaTopic],
                            kafkaConfig: KafkaConfig,
                            deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
                            formatter: RecordFormatter,
                            contextInitializer: ContextInitializer[ConsumerRecord[K, V]]): Source = {
    lazy val initializerFun = contextInitializer.initContext(TypedNodeDependency[NodeId].extract(dependencies).id)
    new LiteKafkaSource with Lifecycle {
      override def open(context: EngineRuntimeContext): Unit = {
        initializerFun.open(context)
      }

      override def topics: List[String] = preparedTopics.map(_.prepared)

      override def deserialize(context: EngineRuntimeContext, record: ConsumerRecord[Array[Byte], Array[Byte]]): Context = {
        val deserialized = deserializationSchema.deserialize(record)
        // TODO: what about other properties based on kafkaConfig?
        initializerFun(deserialized)
      }
    }
  }

}