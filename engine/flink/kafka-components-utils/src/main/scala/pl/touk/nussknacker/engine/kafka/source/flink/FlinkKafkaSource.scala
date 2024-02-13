package pl.touk.nussknacker.engine.kafka.source.flink

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.api.process.{ContextInitializer, TestWithParametersSupport}
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.api.test.{TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.{Context, NodeId}
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.exception.ExceptionHandler
import pl.touk.nussknacker.engine.flink.api.process.{
  FlinkCustomNodeContext,
  FlinkIntermediateRawSource,
  FlinkSource,
  FlinkSourceTestSupport
}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.StandardTimestampWatermarkHandler.SimpleSerializableTimestampAssigner
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{
  StandardTimestampWatermarkHandler,
  TimestampWatermarkHandler
}
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.kafka.serialization.FlinkSerializationSchemaConversions.{
  FlinkDeserializationSchemaWrapper,
  wrapToFlinkDeserializationSchema
}
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.KafkaTestParametersInfo
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSource.defaultMaxOutOfOrdernessMillis
import pl.touk.nussknacker.engine.util.parameters.TestingParametersSupport

import java.time.Duration
import java.util.Properties
import scala.jdk.CollectionConverters._

class FlinkKafkaSource[T](
    preparedTopics: List[PreparedKafkaTopic],
    val kafkaConfig: KafkaConfig,
    deserializationSchema: serialization.KafkaDeserializationSchema[T],
    passedAssigner: Option[TimestampWatermarkHandler[T]],
    val formatter: RecordFormatter,
    testParametersInfo: KafkaTestParametersInfo,
    overriddenConsumerGroup: Option[String] = None,
    namingStrategy: NamingStrategy
) extends FlinkSource
    with FlinkIntermediateRawSource[T]
    with Serializable
    with FlinkSourceTestSupport[T]
    with RecordFormatterBaseTestDataGenerator
    with ExplicitUidInOperatorsSupport
    with TestWithParametersSupport[T] {

  protected lazy val topics: List[String] = preparedTopics.map(_.prepared)

  override def sourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[Context] = {
    val consumerGroupId = prepareConsumerGroupId(flinkNodeContext)
    val sourceFunction  = flinkSourceFunction(consumerGroupId, flinkNodeContext)

    prepareSourceStream(env, flinkNodeContext, sourceFunction)
  }

  override val typeInformation: TypeInformation[T] = {
    wrapToFlinkDeserializationSchema(deserializationSchema).getProducedType
  }

  protected def flinkSourceFunction(
      consumerGroupId: String,
      flinkNodeContext: FlinkCustomNodeContext
  ): SourceFunction[T] = {
    topics.foreach(KafkaUtils.setToLatestOffsetIfNeeded(kafkaConfig, _, consumerGroupId))
    createFlinkSource(consumerGroupId, flinkNodeContext)
  }

  protected def createFlinkSource(
      consumerGroupId: String,
      flinkNodeContext: FlinkCustomNodeContext
  ): SourceFunction[T] = {
    new FlinkKafkaConsumerHandlingExceptions[T](
      topics.asJava,
      wrapToFlinkDeserializationSchema(deserializationSchema),
      KafkaUtils.toConsumerProperties(kafkaConfig, Some(consumerGroupId)),
      flinkNodeContext.exceptionHandlerPreparer,
      flinkNodeContext.convertToEngineRuntimeContext,
      NodeId(flinkNodeContext.nodeId)
    )
  }

  // Flink implementation of testing uses direct output from testDataParser, so we perform deserialization here, in contrast to Lite implementation
  override def testRecordParser: TestRecordParser[T] = (testRecord: TestRecord) => {
    // TODO: we assume parsing for all topics is the same
    val topic = topics.head
    deserializationSchema.deserialize(formatter.parseRecord(topic, testRecord))
  }

  override def timestampAssignerForTest: Option[TimestampWatermarkHandler[T]] = timestampAssigner

  override def timestampAssigner: Option[TimestampWatermarkHandler[T]] = Some(
    passedAssigner.getOrElse(
      new StandardTimestampWatermarkHandler[T](
        WatermarkStrategy
          .forBoundedOutOfOrderness(
            Duration.ofMillis(kafkaConfig.defaultMaxOutOfOrdernessMillis.getOrElse(defaultMaxOutOfOrdernessMillis))
          )
      )
    )
  )

  protected def deserializeTestData(record: ConsumerRecord[Array[Byte], Array[Byte]]): T = {
    deserializationSchema.deserialize(record)
  }

  override def testParametersDefinition: List[Parameter] = testParametersInfo.parametersDefinition

  override def parametersToTestData(params: Map[String, AnyRef]): T = {
    val flatParams = TestingParametersSupport.unflattenParameters(params)
    deserializeTestData(formatter.parseRecord(topics.head, testParametersInfo.createTestRecord(flatParams)))
  }

  private def prepareConsumerGroupId(nodeContext: FlinkCustomNodeContext): String = {
    val baseName = overriddenConsumerGroup.getOrElse(ConsumerGroupDeterminer(kafkaConfig).consumerGroup(nodeContext))
    if (kafkaConfig.useNamingStrategyForConsumerGroupId) {
      namingStrategy.prepareName(baseName)
    } else {
      baseName
    }
  }

}

// TODO: Tricks like deserializationSchema.setExceptionHandlingData and FlinkKafkaConsumer overriding could be replaced by
//       making KafkaDeserializationSchema stupid (just producing ConsumerRecord[Array[Byte], Array[Byte]])
//       and moving deserialization logic to separate flatMap function that would produce Context.
//       Thanks to that contextInitializer.initContext would be wrapped by exception handling mechanism as well.
//       It is done this way in lite engine implementation.
@silent("deprecated")
class FlinkKafkaConsumerHandlingExceptions[T](
    topics: java.util.List[String],
    deserializationSchema: FlinkDeserializationSchemaWrapper[T],
    props: Properties,
    exceptionHandlerPreparer: RuntimeContext => ExceptionHandler,
    convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext,
    nodeId: NodeId
) extends FlinkKafkaConsumer[T](topics, deserializationSchema, props) {

  protected var exceptionHandler: ExceptionHandler = _

  protected var exceptionPurposeContextIdGenerator: ContextIdGenerator = _

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    exceptionHandler = exceptionHandlerPreparer(getRuntimeContext)
    exceptionPurposeContextIdGenerator = convertToEngineRuntimeContext(getRuntimeContext).contextIdGenerator(nodeId.id)
    deserializationSchema.setExceptionHandlingData(exceptionHandler, exceptionPurposeContextIdGenerator, nodeId)
  }

  override def close(): Unit = {
    if (exceptionHandler != null) {
      exceptionHandler.close()
    }
    super.close()
  }

}

class FlinkConsumerRecordBasedKafkaSource[K, V](
    preparedTopics: List[PreparedKafkaTopic],
    kafkaConfig: KafkaConfig,
    deserializationSchema: serialization.KafkaDeserializationSchema[ConsumerRecord[K, V]],
    timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]],
    formatter: RecordFormatter,
    override val contextInitializer: ContextInitializer[ConsumerRecord[K, V]],
    testParametersInfo: KafkaTestParametersInfo,
    namingStrategy: NamingStrategy
) extends FlinkKafkaSource[ConsumerRecord[K, V]](
      preparedTopics,
      kafkaConfig,
      deserializationSchema,
      timestampAssigner,
      formatter,
      testParametersInfo,
      namingStrategy = namingStrategy
    ) {

  override def timestampAssignerForTest: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]] =
    timestampAssigner.orElse(
      Some(
        StandardTimestampWatermarkHandler.afterEachEvent[ConsumerRecord[K, V]](
          (_.timestamp()): SimpleSerializableTimestampAssigner[ConsumerRecord[K, V]]
        )
      )
    )

  override val typeInformation: TypeInformation[ConsumerRecord[K, V]] = {
    TypeInformation.of(classOf[ConsumerRecord[K, V]])
  }

}

object FlinkKafkaSource {
  val defaultMaxOutOfOrdernessMillis = 60000
}
