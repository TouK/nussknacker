package pl.touk.nussknacker.engine.kafka.source.flink

import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.functions.{OpenContext, RuntimeContext}
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSource}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer, FlinkKafkaConsumerBase}
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.{Context, NodeId}
import pl.touk.nussknacker.engine.api.component.StaticParameterConfig
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesWithRadioParameterEditor, Parameter}
import pl.touk.nussknacker.engine.api.deployment.{ScenarioActionName, WithActionParametersSupport}
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{
  ContextInitializer,
  TestDataGenerator,
  TestWithParametersSupport,
  TopicName
}
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.flink.api.{FlinkEngineContext, RuntimeCtx}
import pl.touk.nussknacker.engine.flink.api.datastream.DataStreamImplicits.DataStreamExtension
import pl.touk.nussknacker.engine.flink.api.exception.ExceptionHandler
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.WatermarkStrategyUtils
import pl.touk.nussknacker.engine.flink.watermarkstrategy.FlinkWatermarkStrategyRuntimeHandler
import pl.touk.nussknacker.engine.flink.watermarkstrategy.FlinkWatermarkStrategyRuntimeHandler.{
  ContextInitializingFunction,
  ContextWithEventTime
}
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.kafka.serialization.FlinkSerializationSchemaConversions
import pl.touk.nussknacker.engine.kafka.serialization.FlinkSerializationSchemaConversions.FlinkDeserializationSchemaWrapper
import pl.touk.nussknacker.engine.kafka.source.KafkaTestParametersInfo
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSource.{
  OFFSET_RESET_STRATEGY_LABEL,
  OFFSET_RESET_STRATEGY_PARAM_NAME
}
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.inputParamName
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalToJsonFormatter
import pl.touk.nussknacker.engine.util.parameters.TestingParametersSupport
import pl.touk.nussknacker.engine.util.watermarkstrategy.{WatermarkStrategyOptions, WithWatermarkStrategyOptions}

import java.util
import java.util.Properties
import scala.annotation.nowarn
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

  private val typeInformation = ConsumerRecordTypeInfo[K, V](kafkaComponentsConfig)

  override def contextStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[Context] = {
    val streamOfRaw = sourceStream(env, flinkNodeContext).setUidAndNameToNodeId(flinkNodeContext.nodeId)
    // 1. initialize Context and compute event time
    streamOfRaw
      .flatMap(
        ContextInitializingFunction(
          flinkNodeContext.nodeId,
          flinkNodeContext.convertToEngineRuntimeContext,
          watermarkStrategyOptions.eventTimeLazyParam,
          flinkNodeContext.lazyParameterHelper,
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

  @nowarn("cat=deprecation")
  private def sourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStreamSource[ConsumerRecord[K, V]] = {
    val consumerGroupId = prepareConsumerGroupId(flinkNodeContext)
    val sourceFunction  = flinkSourceFunction(consumerGroupId, flinkNodeContext)
    env.addSource(sourceFunction, typeInformation)
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

  @nowarn("cat=deprecation")
  protected def flinkSourceFunction(
      consumerGroupId: String,
      flinkNodeContext: FlinkCustomNodeContext
  ): SourceFunction[ConsumerRecord[K, V]] = {
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

    createFlinkSource(consumerGroupId, flinkNodeContext)
  }

  @nowarn("cat=deprecation")
  protected def createFlinkSource(
      consumerGroupId: String,
      flinkNodeContext: FlinkCustomNodeContext
  ): SourceFunction[ConsumerRecord[K, V]] = {
    new FlinkKafkaConsumerHandlingExceptions[ConsumerRecord[K, V]](
      topics.map(_.name).toList.asJava,
      FlinkSerializationSchemaConversions.wrapToFlinkDeserializationSchema(deserializationSchema, typeInformation),
      KafkaUtils.toConsumerProperties(kafkaComponentsConfig, Some(consumerGroupId)),
      flinkNodeContext.exceptionHandlerPreparer,
      flinkNodeContext.convertToEngineRuntimeContext,
      flinkNodeContext.nodeId
    )
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

}

// TODO: Tricks like deserializationSchema.setExceptionHandlingData and FlinkKafkaConsumer overriding could be replaced by
//       making KafkaDeserializationSchema stupid (just producing ConsumerRecord[Array[Byte], Array[Byte]])
//       and moving deserialization logic to separate flatMap function that would produce Context.
//       Thanks to that contextInitializer.initContext would be wrapped by exception handling mechanism as well.
//       It is done this way in lite engine implementation.
@nowarn("cat=deprecation")
class FlinkKafkaConsumerHandlingExceptions[T](
    topics: java.util.List[String],
    deserializationSchema: FlinkDeserializationSchemaWrapper[T],
    props: Properties,
    exceptionHandlerPreparer: FlinkEngineContext => ExceptionHandler,
    convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext,
    nodeId: NodeId
) extends FlinkKafkaConsumer[T](topics, deserializationSchema, props)
    with LazyLogging {

  protected var exceptionHandler: ExceptionHandler = _

  private var exceptionPurposeContextIdGenerator: ContextIdGenerator = _

  override def open(openContext: OpenContext): Unit = {
    patchRestoredState()
    super.open(openContext)
    exceptionHandler = exceptionHandlerPreparer(RuntimeCtx(getRuntimeContext))
    exceptionPurposeContextIdGenerator = convertToEngineRuntimeContext(getRuntimeContext).contextIdGenerator(nodeId)
    deserializationSchema.setExceptionHandlingData(exceptionHandler, exceptionPurposeContextIdGenerator, nodeId)
  }

  override def close(): Unit = {
    if (exceptionHandler != null) {
      exceptionHandler.close()
    }
    super.close()
  }

  /**
   * We observed that [[FlinkKafkaConsumerBase]], in `initializeState()`, may set a non-null but empty `restoredState`
   * even though the saved state clearly contained proper state data. This makes the `open()` method treat
   * all partitions as new ones instead of trying to fall back to offsets stored in associated consumer group.
   *
   * This may happen when we are changing Kafka source to a different implementation (but still a compatible one),
   * but also when there are no changes in the scenario. There's possibly some kind of strange state incompatibility,
   * and Flink accepts the old state from savepoint as compatible, but it restores it as empty state.
   *
   * It's impossible to fix Flink's source because it's deprecated and doesn't accept any changes, including bugfixes.
   *
   * To work around this issue we patch `restoredState` value to prevent invalid state restores and treat
   * this situation as a new deployment without state.
   *
   * Note that our change may break a source reading from topics indicated by a pattern which, at the time
   * of snapshot creation, indicates zero partitions. Nussknacker always uses concrete topic names, so for us this
   * is acceptable.
   */
  private def patchRestoredState(): Unit = {
    assert(
      this.isInstanceOf[FlinkKafkaConsumerBase[_]],
      s"$this must be an instance of ${classOf[FlinkKafkaConsumerBase[_]]}"
    )
    val restoredStateField = classOf[FlinkKafkaConsumerBase[_]].getDeclaredField("restoredState")
    restoredStateField.setAccessible(true)
    restoredStateField.get(this) match {
      case null => // there is no restored stare
      case tm: util.TreeMap[_, _] =>
        if (tm.isEmpty) {
          logger.warn("Got empty restoredState, patching it to prevent automatic reset to the earliest offsets")
          // removing state with empty offset list will make the `open` method use its default behavior,
          // i.e. Kafka fetcher will be initialized using configured `startupMode`
          restoredStateField.set(this, null)
        }
      case other =>
        throw new RuntimeException(
          s"Expected restoredState to be of type ${classOf[util.TreeMap[_, _]]} but got ${other.getClass}"
        )
    }
  }

}
