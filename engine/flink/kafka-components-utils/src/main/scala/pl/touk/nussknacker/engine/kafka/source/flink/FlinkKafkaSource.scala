package pl.touk.nussknacker.engine.kafka.source.flink

import cats.data.NonEmptyList
import com.github.ghik.silencer.silent
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.functions.{OpenContext, RuntimeContext}
import org.apache.flink.streaming.api.datastream.DataStreamSource
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer, FlinkKafkaConsumerBase}
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesWithRadioParameterEditor, Parameter}
import pl.touk.nussknacker.engine.api.deployment.{ScenarioActionName, WithActionParametersSupport}
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{ContextInitializer, TestWithParametersSupport, TopicName}
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.api.test.{TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.flink.api.exception.ExceptionHandler
import pl.touk.nussknacker.engine.flink.api.process.{
  FlinkCustomNodeContext,
  FlinkSourceTestSupport,
  StandardFlinkSource,
  StandardFlinkSourceFunctionUtils
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
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSource.OFFSET_RESET_STRATEGY_PARAM_NAME
import pl.touk.nussknacker.engine.util.parameters.TestingParametersSupport

import java.util
import java.util.Properties
import scala.jdk.CollectionConverters._

class FlinkKafkaSource[T](
    preparedTopics: NonEmptyList[PreparedKafkaTopic[TopicName.ForSource]],
    val kafkaConfig: KafkaConfig,
    deserializationSchema: serialization.KafkaDeserializationSchema[T],
    passedAssigner: Option[TimestampWatermarkHandler[T]], // TODO: rename to smth like overridingTimestampAssigner
    val formatter: RecordFormatter,
    override val contextInitializer: ContextInitializer[T],
    testParametersInfo: KafkaTestParametersInfo,
    overriddenConsumerGroup: Option[String] = None,
    namingStrategy: NamingStrategy
) extends StandardFlinkSource[T]
    with Serializable
    with FlinkSourceTestSupport[T]
    with RecordFormatterBaseTestDataGenerator
    with TestWithParametersSupport[T]
    with WithActionParametersSupport
    with LazyLogging {

  @silent("deprecated")
  override def sourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStreamSource[T] = {
    val consumerGroupId = prepareConsumerGroupId(flinkNodeContext)
    val sourceFunction  = flinkSourceFunction(consumerGroupId, flinkNodeContext)
    StandardFlinkSourceFunctionUtils.createSourceStream(
      env = env,
      sourceFunction = sourceFunction,
      typeInformation = wrapToFlinkDeserializationSchema(deserializationSchema).getProducedType
    )
  }

  protected lazy val topics: NonEmptyList[TopicName.ForSource] = preparedTopics.map(_.prepared)

  private val defaultOffsetResetStrategy = kafkaConfig.defaultOffsetResetStrategy.getOrElse(OffsetResetStrategy.None)

  override def actionParametersDefinition: Map[ScenarioActionName, Map[ParameterName, ParameterConfig]] = {
    Map(
      ScenarioActionName.Deploy -> Map(
        OFFSET_RESET_STRATEGY_PARAM_NAME -> ParameterConfig(
          defaultValue = Some(defaultOffsetResetStrategy.toString),
          editor = Some(
            FixedValuesWithRadioParameterEditor(
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
            )
          ),
          validators = None,
          label = Some("Offset reset strategy"),
          hintText = None
        ),
      )
    )
  }

  @silent("deprecated")
  protected def flinkSourceFunction(
      consumerGroupId: String,
      flinkNodeContext: FlinkCustomNodeContext
  ): SourceFunction[T] = {
    val offsetResetStrategy =
      flinkNodeContext.nodeDeploymentData
        .flatMap(_.get(OFFSET_RESET_STRATEGY_PARAM_NAME.value))
        .map(OffsetResetStrategy.withName)
        .getOrElse(defaultOffsetResetStrategy)
    logger.info(
      s"Flink source for scenario ${flinkNodeContext.jobData.processVersion.processName.value} for node ${flinkNodeContext.nodeId} defaultOffsetResetStrategy=${kafkaConfig.defaultOffsetResetStrategy}, offsetResetStrategy=${offsetResetStrategy}"
    )

    offsetResetStrategy match {
      case OffsetResetStrategy.ToLatest =>
        topics.toList.foreach(t => KafkaUtils.setOffsetToLatest(t.name, consumerGroupId, kafkaConfig))
      case OffsetResetStrategy.ToEarliest =>
        topics.toList.foreach(t => KafkaUtils.setOffsetToEarliest(t.name, consumerGroupId, kafkaConfig))
      case OffsetResetStrategy.None =>
        ()
    }

    createFlinkSource(consumerGroupId, flinkNodeContext)
  }

  @silent("deprecated")
  protected def createFlinkSource(
      consumerGroupId: String,
      flinkNodeContext: FlinkCustomNodeContext
  ): SourceFunction[T] = {
    new FlinkKafkaConsumerHandlingExceptions[T](
      topics.map(_.name).toList.asJava,
      wrapToFlinkDeserializationSchema(deserializationSchema),
      KafkaUtils.toConsumerProperties(kafkaConfig, Some(consumerGroupId)),
      flinkNodeContext.exceptionHandlerPreparer,
      flinkNodeContext.convertToEngineRuntimeContext,
      NodeId(flinkNodeContext.nodeId)
    )
  }

  // Flink implementation of testing uses direct output from testDataParser, so we perform deserialization here, in contrast to Lite implementation
  override def testRecordParser: TestRecordParser[T] = (testRecords: List[TestRecord]) =>
    testRecords.map { testRecord =>
      // TODO: we assume parsing for all topics is the same
      val topic = topics.head
      deserializationSchema.deserialize(formatter.parseRecord(topic, testRecord))
    }

  override def timestampAssignerForTest: Option[TimestampWatermarkHandler[T]] = timestampAssigner

  override def timestampAssigner: Option[TimestampWatermarkHandler[T]] = passedAssigner.orElse(
    Some(
      StandardTimestampWatermarkHandler.boundedOutOfOrderness(
        extract = None,
        maxOutOfOrderness = kafkaConfig.defaultMaxOutOfOrdernessMillis,
        idlenessTimeoutDuration = kafkaConfig.idleTimeoutDuration
      )
    )
  )

  protected def deserializeTestData(record: ConsumerRecord[Array[Byte], Array[Byte]]): T = {
    deserializationSchema.deserialize(record)
  }

  override def testParametersDefinition: List[Parameter] = testParametersInfo.parametersDefinition

  override def parametersToTestData(params: Map[ParameterName, AnyRef]): T = {
    val unflattenedParams = TestingParametersSupport.unflattenParameters(params)
    val removedValue = if (unflattenedParams.size == 1) {
      unflattenedParams.head match {
        case ("Value", inner) => inner
        case _                => unflattenedParams
      }
    } else unflattenedParams
    deserializeTestData(
      formatter.parseRecord(
        topics.head,
        testParametersInfo.createTestRecord(removedValue)
      )
    )
  }

  private def prepareConsumerGroupId(nodeContext: FlinkCustomNodeContext): String = {
    val baseName = overriddenConsumerGroup.getOrElse(ConsumerGroupDeterminer(kafkaConfig).consumerGroup(nodeContext))
    namingStrategy.prepareName(baseName)
  }

}

object FlinkKafkaSource {
  val OFFSET_RESET_STRATEGY_PARAM_NAME: ParameterName = ParameterName("offsetResetStrategy")
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
) extends FlinkKafkaConsumer[T](topics, deserializationSchema, props)
    with LazyLogging {

  protected var exceptionHandler: ExceptionHandler = _

  protected var exceptionPurposeContextIdGenerator: ContextIdGenerator = _

  override def open(openContext: OpenContext): Unit = {
    patchRestoredState()
    super.open(openContext)
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

class FlinkConsumerRecordBasedKafkaSource[K, V](
    preparedTopics: NonEmptyList[PreparedKafkaTopic[TopicName.ForSource]],
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
      contextInitializer,
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

}
