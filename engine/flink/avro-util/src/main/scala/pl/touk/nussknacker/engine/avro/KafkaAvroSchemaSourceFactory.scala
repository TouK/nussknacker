package pl.touk.nussknacker.engine.avro

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.test.TestParsingUtils
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.kafka._

class KafkaAvroSchemaSourceFactory[T: TypeInformation](schemaAvroProvider: SchemaAvroProvider[T],
                                                                processObjectDependencies: ProcessObjectDependencies,
                                                                timestampAssigner: Option[TimestampAssigner[T]])
  extends BaseKafkaSourceFactory(timestampAssigner, TestParsingUtils.newLineSplit, processObjectDependencies) {

  protected def createSource(processMetaData: MetaData, topics: List[String], version: Option[Int], avro: Option[String]): KafkaSource =
    new KafkaSource(
      consumerGroupId = processMetaData.id,
      topics,
      schemaAvroProvider.deserializationSchemaFactory(avro).create(topics, kafkaConfig),
      schemaAvroProvider.recordFormatter(topics.head, avro),
      processObjectDependencies
    ) with ReturningType {
      override def returnType: typing.TypingResult = schemaAvroProvider.typeDefinition(topics.head, version, avro)
    }
}
