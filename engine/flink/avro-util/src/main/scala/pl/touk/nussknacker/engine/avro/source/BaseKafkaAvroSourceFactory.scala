package pl.touk.nussknacker.engine.avro.source

import org.apache.avro.specific.{SpecificRecord, SpecificRecordBase}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.formats.avro.typeutils.{AvroTypeInfo, GenericRecordAvroTypeInfo}
import org.apache.flink.streaming.api.functions.TimestampAssigner
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.test.TestParsingUtils
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.avro.{KafkaAvroFactory, KafkaAvroSchemaProvider}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceFactory
import pl.touk.nussknacker.engine.flink.util.timestamp.BounedOutOfOrderPreviousElementAssigner
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.kafka.source.KafkaSource

import scala.reflect.ClassTag

abstract class BaseKafkaAvroSourceFactory[T: ClassTag](processObjectDependencies: ProcessObjectDependencies,
                                                       timestampAssigner: Option[TimestampAssigner[T]])
  extends FlinkSourceFactory[T] with Serializable {

  private val defaultMaxOutOfOrdernessMillis = 60000

  // We currently not using processMetaData and nodeId but it is here in case if someone want to use e.g. some additional fields
  // in their own concrete implementation
  def createSource(preparedTopic: PreparedKafkaTopic,
                   kafkaConfig: KafkaConfig,
                   kafkaAvroSchemaProvider: KafkaAvroSchemaProvider[T],
                   processMetaData: MetaData,
                   nodeId: NodeId): KafkaSource[T] with ReturningType = {

    val returnTypeDefinition = kafkaAvroSchemaProvider.returnType(KafkaAvroFactory.handleSchemaRegistryError)

    // See Flink's AvroDeserializationSchema
    implicit val typeInformation: TypeInformation[T] = {
      if (classOf[SpecificRecord].isAssignableFrom(clazz))
        new AvroTypeInfo(clazz.asInstanceOf[Class[_ <: SpecificRecordBase]]).asInstanceOf[TypeInformation[T]]
      else
        new GenericRecordAvroTypeInfo(kafkaAvroSchemaProvider.fetchTopicValueSchema.valueOr(throw _)).asInstanceOf[TypeInformation[T]]
    }

    new KafkaSource(
      List(preparedTopic),
      kafkaConfig,
      kafkaAvroSchemaProvider.deserializationSchema,
      assignerToUse(kafkaConfig),
      kafkaAvroSchemaProvider.recordFormatter,
      TestParsingUtils.newLineSplit
    ) with ReturningType {
      override def returnType: typing.TypingResult = returnTypeDefinition
    }
  }

  protected def assignerToUse(kafkaConfig: KafkaConfig): Option[TimestampAssigner[T]] = {
    Some(timestampAssigner.getOrElse(
      new BounedOutOfOrderPreviousElementAssigner[T](kafkaConfig.defaultMaxOutOfOrdernessMillis
        .getOrElse(defaultMaxOutOfOrdernessMillis))
    ))
  }
}
