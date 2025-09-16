package pl.touk.nussknacker.engine.kafka.source.flink

import cats.data.NonEmptyList
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import pl.touk.nussknacker.engine.api.livedata.{DataRecord, DataRecords, LiveDataProvider}
import pl.touk.nussknacker.engine.api.process.{ContextInitializer, Source, TopicName}
import pl.touk.nussknacker.engine.kafka.{serialization, KafkaComponentsConfig}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalToJsonFormatter

trait KafkaLiveDataProvider[K, V] extends LiveDataProvider { self: Source =>

  protected val topics: NonEmptyList[TopicName.ForSource]

  protected val kafkaComponentsConfig: KafkaComponentsConfig

  // TODO: Instead of using UniversalToJsonFormatter designed for the old, source-specific format, rewrite these things
  //       from scratch.
  protected val formatter: UniversalToJsonFormatter[K, V]

  protected val deserializationSchema: serialization.KafkaDeserializationSchema[ConsumerRecord[K, V]]

  protected val contextInitializer: ContextInitializer[ConsumerRecord[K, V]]

  override def fetchLiveData(maxNumberOfRecords: Int): DataRecords = {
    val records = formatter
      .generateTestData(topics, maxNumberOfRecords, kafkaComponentsConfig)
      .testRecords
      .map(formatter.parseRecord(topics.head, _))
      .map(deserializationSchema.deserialize)
      .map { consumerRecord =>
        val variables = contextInitializer.convertToInitialVariables(consumerRecord)
        // TODO: Respect Event time parameter
        val timestamp = Option(consumerRecord.timestamp())
          .filterNot(_ => consumerRecord.timestampType() == TimestampType.NO_TIMESTAMP_TYPE)
        DataRecord(variables.variables, timestamp)
      }
    DataRecords(records)
  }

}
