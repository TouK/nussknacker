package pl.touk.nussknacker.engine.lite.kafka

import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.{
  AuthorizationException,
  InterruptException,
  OutOfOrderSequenceException,
  ProducerFencedException
}
import pl.touk.nussknacker.engine.api.exception.WithExceptionExtractor
import pl.touk.nussknacker.engine.api.namespaces.{NamespaceContext, NamingStrategy}
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.{MetaData, VariableConstants}
import pl.touk.nussknacker.engine.kafka.KafkaUtils
import pl.touk.nussknacker.engine.kafka.exception.{
  DefaultKafkaErrorTopicInitializer,
  KafkaJsonExceptionSerializationSchema
}
import pl.touk.nussknacker.engine.lite.ScenarioInterpreterFactory.ScenarioInterpreterWithLifecycle
import pl.touk.nussknacker.engine.lite.api.commonTypes.{ErrorType, ResultType}
import pl.touk.nussknacker.engine.lite.api.interpreterTypes
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{ScenarioInputBatch, SourceId}
import pl.touk.nussknacker.engine.lite.kafka.KafkaTransactionalScenarioInterpreter.{
  Input,
  KafkaInterpreterConfig,
  Output
}
import pl.touk.nussknacker.engine.lite.kafka.api.LiteKafkaSource
import pl.touk.nussknacker.engine.lite.metrics.SourceMetrics
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.exception.DefaultWithExceptionExtractor

import scala.compat.java8.DurationConverters.FiniteDurationops
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

class KafkaSingleScenarioTaskRun(
    taskId: String,
    metaData: MetaData,
    runtimeContext: EngineRuntimeContext,
    engineConfig: KafkaInterpreterConfig,
    interpreter: ScenarioInterpreterWithLifecycle[Future, Input, Output],
    sourceMetrics: SourceMetrics,
    namingStrategy: NamingStrategy
)(implicit ec: ExecutionContext)
    extends Task
    with LazyLogging {

  private val groupId = namingStrategy.prepareName(metaData.name.value, NamespaceContext.KafkaTopic)

  private var consumer: KafkaConsumer[Array[Byte], Array[Byte]] = _
  private var producer: KafkaProducerRecordsHandler             = _

  private var consumerMetricsRegistrar: KafkaMetricsRegistrar = _
  private var producerMetricsRegistrar: KafkaMetricsRegistrar = _

  // TODO: consider more elastic extractor definition (e.g. via configuration, as it is in flink executor)
  protected val extractor: WithExceptionExtractor = new DefaultWithExceptionExtractor

  private val sourceToTopic: Map[TopicName.ForSource, Map[SourceId, LiteKafkaSource]] = interpreter.sources
    .flatMap {
      case (sourceId, kafkaSource: LiteKafkaSource) =>
        kafkaSource.topics.map(topic => topic -> (sourceId, kafkaSource)).toList
      case (sourceId, other) =>
        throw new IllegalArgumentException(s"Unexpected source: $other for ${sourceId.value}")
    }
    .groupBy(_._1)
    .mapValuesNow(_.values.toMap)

  def init(): Unit = {
    configSanityCheck()
    new DefaultKafkaErrorTopicInitializer(engineConfig.kafka, engineConfig.exceptionHandlingConfig).init()

    producer = prepareProducer
    consumer = prepareConsumer
    consumer.subscribe(sourceToTopic.keys.map(_.name).toSet.asJavaCollection)

    registerMetrics()
  }

  private def prepareConsumer: KafkaConsumer[Array[Byte], Array[Byte]] = {
    val consumerProperties = KafkaUtils.toConsumerProperties(engineConfig.kafka, Some(groupId))
    producer.enrichConsumerProperties(consumerProperties)
    new KafkaConsumer[Array[Byte], Array[Byte]](consumerProperties)
  }

  private def prepareProducer: KafkaProducerRecordsHandler = {
    val producerProperties = KafkaUtils.toProducerProperties(engineConfig.kafka, clientId = groupId)
    KafkaProducerRecordsHandler.apply(engineConfig, producerProperties, groupId)
  }

  private def registerMetrics(): Unit = {
    consumerMetricsRegistrar = new KafkaMetricsRegistrar(taskId, consumer.metrics(), runtimeContext.metricsProvider)
    consumerMetricsRegistrar.registerMetrics()
    producerMetricsRegistrar = new KafkaMetricsRegistrar(taskId, producer.metrics(), runtimeContext.metricsProvider)
    producerMetricsRegistrar.registerMetrics()
  }

  // We have both "mostly" side-effect-less interpreter.invoke and sendOutputToKafka in a body of transaction to avoid situation
  // when beginTransaction fails and we keep restarting interpreter.invoke which can cause e.g. sending many unnecessary requests
  // to rest services. beginTransaction is costless (doesn't communicate with transaction coordinator)
  def run(): Unit = {
    val records = consumer.poll(engineConfig.pollDuration.toJava)
    if (records.isEmpty) {
      logger.trace("No records, skipping")
      return
    }
    producer.beforeRecordsProcessing()
    try {
      processRecords(records)
      producer.onRecordsSuccessfullyProcessed(records, consumer)
    } catch {
      // Those are rather not our cases but their shouldn't cause transaction abortion:
      // https://stackoverflow.com/a/63837803
      case e @ (_: ProducerFencedException | _: OutOfOrderSequenceException | _: AuthorizationException) =>
        logger.warn(s"Fatal producer error: ${e.getMessage}. Closing producer without abort transaction")
        throw e
      case NonFatal(e) =>
        logger.warn(s"Unhandled error: ${e.getMessage}. Aborting kafka transaction")
        producer.onRecordsProcessingFailure()
        throw e
    }
  }

  private def processRecords(records: ConsumerRecords[Array[Byte], Array[Byte]]) = {
    val valuesToRun = prepareRecords(records)
    val output      = Await.result(interpreter.invoke(ScenarioInputBatch(valuesToRun)), engineConfig.interpreterTimeout)
    Await.result(sendOutputToKafka(output), engineConfig.publishTimeout)
  }

  private def prepareRecords(
      records: ConsumerRecords[Array[Byte], Array[Byte]]
  ): List[(SourceId, ConsumerRecord[Array[Byte], Array[Byte]])] = {
    sourceToTopic.toList.flatMap { case (topic, sourcesSubscribedOnTopic) =>
      val forTopic = records.records(topic.name).asScala.toList
      // TODO: try to handle source metrics in more generic way?
      sourcesSubscribedOnTopic.keys.foreach(sourceId =>
        forTopic.foreach(record => sourceMetrics.markElement(sourceId, record.timestamp()))
      )
      sourcesSubscribedOnTopic.keys.toList.flatMap { sourceId => forTopic.map((sourceId, _)) }
    }
  }

  private def sendOutputToKafka(
      output: ResultType[interpreterTypes.EndResult[ProducerRecord[Array[Byte], Array[Byte]]]]
  ): Future[_] = {

    val resultsWithEventTimestamp = output.value.map(endResult => {
      val contextEventTimestamp = endResult.context.get[java.lang.Long](VariableConstants.EventTimestampVariableName)
      val producerRecord        = endResult.result
      new ProducerRecord[Array[Byte], Array[Byte]](
        producerRecord.topic,
        producerRecord.partition,
        Option(producerRecord.timestamp()).orElse(contextEventTimestamp).orNull,
        producerRecord.key,
        producerRecord.value,
        producerRecord.headers
      )
    })

    val errors = output.written.map(serializeError)
    (resultsWithEventTimestamp ++ errors).map(producer.send).sequence
  }

  // TODO: test behaviour on transient exceptions
  private def serializeError(error: ErrorType): ProducerRecord[Array[Byte], Array[Byte]] = {
    val nonTransient = extractor.extractOrThrow(error)
    val schema       = new KafkaJsonExceptionSerializationSchema(metaData, engineConfig.exceptionHandlingConfig)
    schema.serialize(nonTransient, System.currentTimeMillis())
  }

  // Errors from this method will be considered as fatal, handled by uncaughtExceptionHandler and probably causing System.exit
  def close(): Unit = {
    List(producer, consumer, producerMetricsRegistrar, consumerMetricsRegistrar)
      .filter(_ != null)
      .foreach(closeable => retryCloseOnInterrupt(closeable.close))
    logger.info(s"Closed runner for ${metaData.name}")
  }

  private def configSanityCheck(): Unit = {
    val consumerProperties = KafkaUtils.toConsumerProperties(engineConfig.kafka, None)
    val maxPollInterval = new ConsumerConfig(consumerProperties).getInt(CommonClientConfigs.MAX_POLL_INTERVAL_MS_CONFIG)
    if (maxPollInterval <= (engineConfig.interpreterTimeout + engineConfig.publishTimeout).toMillis) {
      throw new IllegalArgumentException(
        s"publishTimeout + interpreterTimeout cannot exceed " +
          s"${CommonClientConfigs.MAX_POLL_INTERVAL_MS_CONFIG}"
      )
    }
  }

  // it may happen that interrupt signal will be mixed in closing process. We want to close
  // normally, so we retry close action - but only once, as we expect only one interrupt call
  private def retryCloseOnInterrupt(action: () => Unit): Unit = {
    try {
      action()
    } catch {
      case _: InterruptedException | _: InterruptException =>
        // This is important - as it's the only way to clear interrupted flag...
        val wasInterrupted = Thread.interrupted()
        logger.debug(s"Interrupted during close: $wasInterrupted, trying once more")
        action()
    }
  }

}
