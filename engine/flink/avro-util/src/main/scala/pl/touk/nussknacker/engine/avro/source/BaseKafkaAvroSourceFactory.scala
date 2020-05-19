package pl.touk.nussknacker.engine.avro.source

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.test.TestParsingUtils
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.avro.{KafkaAvroSchemaProvider, KafkaAvroFactory}
import pl.touk.nussknacker.engine.kafka._

abstract class BaseKafkaAvroSourceFactory[T: TypeInformation](processObjectDependencies: ProcessObjectDependencies, timestampAssigner: Option[TimestampAssigner[T]])
  extends BaseKafkaSourceFactory(timestampAssigner, TestParsingUtils.newLineSplit, processObjectDependencies) {

  // We currently not using processMetaData and nodeId but it is here in case if someone want to use e.g. some additional fields
  // in their own concrete implementation
  def createKafkaAvroSource(topic: String,
                            kafkaConfig: KafkaConfig,
                            kafkaAvroSchemaProvider: KafkaAvroSchemaProvider[T],
                            processMetaData: MetaData,
                            nodeId: NodeId): KafkaSource with ReturningType = {

    val returnTypeDefinition = kafkaAvroSchemaProvider.returnType(KafkaAvroFactory.handleSchemaRegistryError)

    new KafkaSource(
      List(topic),
      kafkaConfig,
      kafkaAvroSchemaProvider.deserializationSchema,
      kafkaAvroSchemaProvider.recordFormatter,
      processObjectDependencies
    ) with ReturningType {
      override def returnType: typing.TypingResult = returnTypeDefinition
    }
  }
}
