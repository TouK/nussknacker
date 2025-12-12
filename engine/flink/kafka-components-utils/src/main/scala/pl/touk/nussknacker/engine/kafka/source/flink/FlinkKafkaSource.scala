package pl.touk.nussknacker.engine.kafka.source.flink

import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.typeinfo.{TypeInformation, Types}
import org.apache.flink.api.connector.source.Source
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.util.Collector
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, NodeId}
import pl.touk.nussknacker.engine.api.component.StaticParameterConfig
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesWithRadioParameterEditor, Parameter}
import pl.touk.nussknacker.engine.api.deployment.{ScenarioActionName, WithActionParametersSupport}
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.WatermarkStrategyUtils
import pl.touk.nussknacker.engine.flink.watermarkstrategy.FlinkWatermarkStrategyRuntimeHandler
import pl.touk.nussknacker.engine.flink.watermarkstrategy.FlinkWatermarkStrategyRuntimeHandler.ContextWithEventTime
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.kafka.source.KafkaTestParametersInfo
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSource.{
  noopDeserializationSchema,
  FlinkKafkaSourceContextInitializingFunction,
  OFFSET_RESET_STRATEGY_LABEL,
  OFFSET_RESET_STRATEGY_PARAM_NAME
}
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.inputParamName
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalToJsonFormatter
import pl.touk.nussknacker.engine.util.parameters.TestingParametersSupport
import pl.touk.nussknacker.engine.util.watermarkstrategy.{WatermarkStrategyOptions, WithWatermarkStrategyOptions}

import java.time.Instant
import scala.jdk.CollectionConverters._

class FlinkKafkaSource[K, V](
    preparedTopics: NonEmptyList[PreparedKafkaTopic[TopicName.ForSource]],
    protected override val kafkaComponentsConfig: KafkaComponentsConfig,
    protected override val deserializationSchema: serialization.KafkaDeserializationSchema[ConsumerRecord[K, V]],
    protected override val formatter: UniversalToJsonFormatter[K, V],
    override val contextInitializer: ContextInitializer[ConsumerRecord[K, V]],
    testParametersInfo: KafkaTestParametersInfo,
    namingStrategy: NamingStrategy,
    override val watermarkStrategyOptions: WatermarkStrategyOptions
) extends FlinkSource
    with Serializable
    // These mixins below are for scenario testing mechanism using source-specific test data format
    with FlinkSourceTestSupport[ConsumerRecord[K, V]]
    with TestDataGenerator
    with TestWithParametersSupport[ConsumerRecord[K, V]]
    with CustomizableContextInitializerSource[ConsumerRecord[K, V]]
    // end
    with WithActionParametersSupport
    with LazyLogging
    with KafkaLiveDataProvider[K, V]
    with WithWatermarkStrategyOptions {

  override def contextStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[Context] = {
    // FIXME: exception handling during deserialization
    env
      .fromSource(
        flinkSource(flinkNodeContext),
        WatermarkStrategy.noWatermarks(),
        flinkNodeContext.nodeId.id
      )
      .uid(flinkNodeContext.nodeId.id)
      // 1. deserialize event, initialize Context and compute event time
      .flatMap(
        new FlinkKafkaSourceContextInitializingFunction[K, V](
          flinkNodeContext.nodeId,
          flinkNodeContext.convertToEngineRuntimeContext,
          watermarkStrategyOptions.eventTimeLazyParam,
          flinkNodeContext.lazyParameterHelper,
          deserializationSchema,
          contextInitializer
        ),
        FlinkWatermarkStrategyRuntimeHandler.contextInitializingFunctionOutputTypeInfo(
          flinkNodeContext.asOneOutputContext
        )
      )
      // 2. assign timestamp and watermarks
      .assignTimestampsAndWatermarks(
        FlinkWatermarkStrategyRuntimeHandler.watermarkStrategy(watermarkStrategyOptions)
      )
      // 3. unwrap context
      .map((ctxWithEventTime: ContextWithEventTime) => ctxWithEventTime.context, flinkNodeContext.contextTypeInfo)
  }

  protected lazy val topics: NonEmptyList[TopicName.ForSource] = preparedTopics.map(_.prepared)

  private val defaultOffsetResetStrategy =
    kafkaComponentsConfig.defaultOffsetResetStrategy.getOrElse(OffsetResetStrategy.None)

  override def actionParametersDefinition: Map[ScenarioActionName, Map[ParameterName, StaticParameterConfig]] = {
    Map(
      ScenarioActionName.Deploy -> Map(
        OFFSET_RESET_STRATEGY_PARAM_NAME -> StaticParameterConfig(
          defaultValue = Some(defaultOffsetResetStrategy.toString),
          editor = FixedValuesWithRadioParameterEditor(
            List(
              FixedExpressionValue(
                OffsetResetStrategy.None.toString,
                s"Resume reading where it previously stopped"
              ),
              FixedExpressionValue(
                OffsetResetStrategy.ToLatest.toString,
                "Read new messages only"
              ),
              FixedExpressionValue(
                OffsetResetStrategy.ToEarliest.toString,
                "Read all messages from the topic"
              ),
            )
          ),
          validators = None,
          label = Some(OFFSET_RESET_STRATEGY_LABEL),
          hintText = None
        ),
      ),
      ScenarioActionName.Redeploy -> Map(
        OFFSET_RESET_STRATEGY_PARAM_NAME -> StaticParameterConfig(
          defaultValue = Some(OffsetResetStrategy.None.toString),
          editor = FixedValuesWithRadioParameterEditor(
            List(
              FixedExpressionValue(
                OffsetResetStrategy.None.toString,
                s"Resume reading where it previously stopped"
              ),
            )
          ),
          validators = None,
          label = Some(OFFSET_RESET_STRATEGY_LABEL),
          hintText = None
        ),
      ),
    )
  }

  private def flinkSource(
      flinkNodeContext: FlinkCustomNodeContext
  ): Source[ConsumerRecord[Array[Byte], Array[Byte]], _, _] = {
    val consumerGroupId = prepareConsumerGroupId(flinkNodeContext)

    // FIXME abr use setStartingOffsets(OffsetsInitializer) instead
    val offsetResetStrategy =
      flinkNodeContext.componentUseContext.deploymentData
        .flatMap(_.get(OFFSET_RESET_STRATEGY_PARAM_NAME.value))
        .map(OffsetResetStrategy.withName)
        .getOrElse(defaultOffsetResetStrategy)
    logger.info(
      s"Flink source for scenario ${flinkNodeContext.jobData.processVersion.processName.value} for node ${flinkNodeContext.nodeId} defaultOffsetResetStrategy=${kafkaComponentsConfig.defaultOffsetResetStrategy}, offsetResetStrategy=${offsetResetStrategy}"
    )

    offsetResetStrategy match {
      case OffsetResetStrategy.ToLatest =>
        topics.toList.foreach(t => KafkaUtils.setOffsetToLatest(t.name, consumerGroupId, kafkaComponentsConfig))
      case OffsetResetStrategy.ToEarliest =>
        topics.toList.foreach(t => KafkaUtils.setOffsetToEarliest(t.name, consumerGroupId, kafkaComponentsConfig))
      case OffsetResetStrategy.None =>
        ()
    }

    KafkaSource
      .builder()
      .setTopics(topics.map(_.name).toList.asJava)
      .setDeserializer(noopDeserializationSchema)
      .setProperties(KafkaUtils.toConsumerProperties(kafkaComponentsConfig, Some(consumerGroupId)))
      .build()
  }

  // Flink implementation of testing uses direct output from testDataParser, so we perform deserialization here, in contrast to Lite implementation
  override def testRecordParser: TestRecordParser[ConsumerRecord[K, V]] = (testRecords: List[TestRecord]) =>
    testRecords.map { testRecord =>
      // TODO: we assume parsing for all topics is the same
      val topic = topics.head
      deserializationSchema.deserialize(formatter.parseRecord(topic, testRecord))
    }

  override def watermarkStrategyForTest: Option[WatermarkStrategy[ConsumerRecord[K, V]]] =
    Some(
      WatermarkStrategyUtils.afterEachEvent[ConsumerRecord[K, V]]((record: ConsumerRecord[K, V], _) =>
        record.timestamp()
      )
    )

  override def generateTestData(maxNumberOfRecords: Int): TestData =
    formatter.generateTestData(topics, maxNumberOfRecords, kafkaComponentsConfig)

  override def testParametersDefinition: List[Parameter] = testParametersInfo.parametersDefinition

  override def parametersToTestData(params: Map[ParameterName, AnyRef]): ConsumerRecord[K, V] = {
    val unflattenedParams = TestingParametersSupport.unflattenParameters(params)
    val removedValue = if (unflattenedParams.size == 1) {
      unflattenedParams.head match {
        case (`inputParamName`.`value`, inner) => inner
        case _                                 => unflattenedParams
      }
    } else unflattenedParams
    deserializeTestData(
      formatter.parseRecord(
        topics.head,
        testParametersInfo.createTestRecord(removedValue)
      )
    )
  }

  private def deserializeTestData(record: ConsumerRecord[Array[Byte], Array[Byte]]): ConsumerRecord[K, V] = {
    deserializationSchema.deserialize(record)
  }

  private def prepareConsumerGroupId(nodeContext: FlinkCustomNodeContext): String = {
    val baseName = ConsumerGroupDeterminer(kafkaComponentsConfig).consumerGroup(nodeContext)
    namingStrategy.prepareName(baseName)
  }

}

object FlinkKafkaSource {
  val OFFSET_RESET_STRATEGY_PARAM_NAME: ParameterName = ParameterName("offsetResetStrategy")
  val OFFSET_RESET_STRATEGY_LABEL: String             = "Offset reset strategy"

  private class FlinkKafkaSourceContextInitializingFunction[K, V](
      nodeId: NodeId,
      convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext,
      eventTimeLazyParam: LazyParameter[Instant],
      lazyParamHelper: FlinkLazyParameterFunctionHelper,
      deserializationSchema: serialization.KafkaDeserializationSchema[ConsumerRecord[K, V]],
      contextInitializer: ContextInitializer[ConsumerRecord[K, V]]
  ) extends FlinkWatermarkStrategyRuntimeHandler.ContextInitializingFunction[ConsumerRecord[Array[Byte], Array[Byte]]](
        nodeId,
        convertToEngineRuntimeContext,
        eventTimeLazyParam,
        lazyParamHelper
      ) {

    override protected def sourceOutputVariables(
        inputRecord: ConsumerRecord[Array[Byte], Array[Byte]],
        initialContext: Context
    ): ContextVariables = {
      val deserializedRecord = deserializationSchema.deserialize(inputRecord)
      contextInitializer.convertToInitialVariables(deserializedRecord)
    }

  }

  private val noopDeserializationSchema =
    new KafkaRecordDeserializationSchema[ConsumerRecord[Array[Byte], Array[Byte]]] {
      override def deserialize(
          record: ConsumerRecord[Array[Byte], Array[Byte]],
          out: Collector[ConsumerRecord[Array[Byte], Array[Byte]]]
      ): Unit = out.collect(record)

      override def getProducedType: TypeInformation[ConsumerRecord[Array[Byte], Array[Byte]]] =
        new ConsumerRecordTypeInfo[Array[Byte], Array[Byte]](
          Types.PRIMITIVE_ARRAY(Types.BYTE).asInstanceOf[TypeInformation[Array[Byte]]],
          Types.PRIMITIVE_ARRAY(Types.BYTE).asInstanceOf[TypeInformation[Array[Byte]]]
        )
    }

}
