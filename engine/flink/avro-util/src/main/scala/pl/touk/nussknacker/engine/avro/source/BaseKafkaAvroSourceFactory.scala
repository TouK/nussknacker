package pl.touk.nussknacker.engine.avro.source

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.{AssignerWithPeriodicWatermarks, TimestampAssigner}
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.watermark.Watermark
import org.apache.flink.streaming.api.windowing.time.Time
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

abstract class BaseKafkaAvroSourceFactory[T: TypeInformation](processObjectDependencies: ProcessObjectDependencies,
                                                              timestampAssigner: Option[TimestampAssigner[T]])
  extends FlinkSourceFactory[T] with Serializable {

  private val defaultMaxOutOfOrdernessMillis = 60000

  // We currently not using processMetaData and nodeId but it is here in case if someone want to use e.g. some additional fields
  // in their own concrete implementation
  def createSource(topic: String,
                   kafkaConfig: KafkaConfig,
                   kafkaAvroSchemaProvider: KafkaAvroSchemaProvider[T],
                   processMetaData: MetaData,
                   nodeId: NodeId): KafkaSource[T] with ReturningType = {

    val returnTypeDefinition = kafkaAvroSchemaProvider.returnType(KafkaAvroFactory.handleSchemaRegistryError)

    new KafkaSource(
      List(topic),
      kafkaConfig,
      kafkaAvroSchemaProvider.deserializationSchema,
      assignerToUse(kafkaConfig),
      kafkaAvroSchemaProvider.recordFormatter,
      TestParsingUtils.newLineSplit,
      processObjectDependencies
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
