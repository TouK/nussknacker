package pl.touk.nussknacker.engine.lite.kafka

import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecords, KafkaConsumer, OffsetAndMetadata}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.InterruptException
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.{Context, MetaData}
import pl.touk.nussknacker.engine.lite.ScenarioInterpreterFactory.ScenarioInterpreterWithLifecycle
import pl.touk.nussknacker.engine.lite.api.commonTypes.{ErrorType, ResultType}
import pl.touk.nussknacker.engine.lite.api.interpreterTypes
import pl.touk.nussknacker.engine.lite.api.interpreterTypes.{ScenarioInputBatch, SourceId}
import pl.touk.nussknacker.engine.lite.kafka.KafkaTransactionalScenarioInterpreter.{EngineConfig, Output}
import pl.touk.nussknacker.engine.lite.kafka.api.LiteKafkaSource
import pl.touk.nussknacker.engine.kafka.KafkaUtils
import pl.touk.nussknacker.engine.lite.metrics.SourceMetrics
import pl.touk.nussknacker.engine.kafka.exception.KafkaJsonExceptionSerializationSchema
import pl.touk.nussknacker.engine.util.exception.WithExceptionExtractor

import java.util.UUID
import scala.compat.java8.DurationConverters.FiniteDurationops
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.CollectionConverters.{asJavaCollectionConverter, asScalaIteratorConverter, iterableAsScalaIterableConverter, mapAsJavaMapConverter}

class KafkaSingleScenarioTaskRun(taskId: String,
                                 metaData: MetaData,
                                 runtimeContext: EngineRuntimeContext,
                                 engineConfig: EngineConfig,
                                 interpreter: ScenarioInterpreterWithLifecycle[Future, Output],
                                 sourceMetrics: SourceMetrics)
                                (implicit ec: ExecutionContext) extends Task with LazyLogging {

  private val groupId = metaData.id

  private var consumer: KafkaConsumer[Array[Byte], Array[Byte]] = _
  private var producer: KafkaProducer[Array[Byte], Array[Byte]] = _

  private val sourceToTopic: Map[String, Map[SourceId, LiteKafkaSource]] = interpreter.sources.flatMap {
    case (sourceId, kafkaSource: LiteKafkaSource) =>
      kafkaSource.topics.map(topic => topic -> (sourceId, kafkaSource))
    case (sourceId, other) => throw new IllegalArgumentException(s"Unexpected source: $other for ${sourceId.value}")
  }.groupBy(_._1).mapValues(_.values.toMap)

  def init(): Unit = {
    configSanityCheck()

    val producerProps = KafkaUtils.toProducerProperties(engineConfig.kafka, groupId)
    //FIXME generate correct id - how to connect to topic/partition??
    producerProps.put("transactional.id", groupId + UUID.randomUUID().toString)
    consumer = new KafkaConsumer[Array[Byte], Array[Byte]](KafkaUtils.toPropertiesForConsumer(engineConfig.kafka, Some(groupId)))
    producer = new KafkaProducer[Array[Byte], Array[Byte]](producerProps)

    producer.initTransactions()
    consumer.subscribe(sourceToTopic.keys.toSet.asJavaCollection)
    val metrics = new KafkaMetrics(taskId, runtimeContext.metricsProvider)
    metrics.registerMetrics(producer.metrics())
    metrics.registerMetrics(consumer.metrics())
  }

  //We process all records, wait for outputs and only then send results in trsnaction
  //This way we trade latency (because first event in batch has to wait for all other)
  //for short transaction (we start only when all external invocations etc. are completed)
  def run(): Unit = {

    val records = consumer.poll(engineConfig.pollDuration.toJava)
    if (records.isEmpty) {
      logger.trace("No records, skipping")
      return
    }

    val valuesToRun = prepareRecords(records)
    //we process batch, assuming that no side effects appear here
    val output = Await.result(interpreter.invoke(ScenarioInputBatch(valuesToRun)), engineConfig.interpreterTimeout)

    //we send all results - offsets, errors and results in one transactions
    producer.beginTransaction()
    val sendFuture = sendOutputToKafka(output)
    Await.result(sendFuture, engineConfig.publishTimeout)
    val offsetsMap: Map[TopicPartition, OffsetAndMetadata] = retrieveMaxOffsetsOffsets(records)
    producer.sendOffsetsToTransaction(offsetsMap.asJava, groupId)
    producer.commitTransaction()
  }


  private def prepareRecords(records: ConsumerRecords[Array[Byte], Array[Byte]]): List[(SourceId, Context)] = {
    sourceToTopic.toList.flatMap {
      case (topic, sourcesSubscribedOnTopic) =>
        val forTopic = records.records(topic).asScala.toList
        //TODO: try to handle source metrics in more generic way?
        sourcesSubscribedOnTopic.keys.foreach(sourceId => forTopic.foreach(record => sourceMetrics.markElement(sourceId, record.timestamp())))
        sourcesSubscribedOnTopic.mapValues(source => forTopic.map(source.deserialize(runtimeContext, _))).toList.flatMap {
          case (sourceId, contexts) => contexts.map((sourceId, _))
        }
    }
  }

  private def sendOutputToKafka(output: ResultType[interpreterTypes.EndResult[ProducerRecord[Array[Byte], Array[Byte]]]]): Future[_] = {
    val results = output.value.map(_.result)
    val errors = output.written.map(serializeError)
    (results ++ errors).map(KafkaUtils.sendToKafka(_)(producer)).sequence
  }

  //TODO: test behaviour on transient exceptions
  private def serializeError(error: ErrorType): ProducerRecord[Array[Byte], Array[Byte]] = {
    val nonTransient = WithExceptionExtractor.extractOrThrow(error)
    KafkaJsonExceptionSerializationSchema(metaData, engineConfig.exceptionHandlingConfig).serialize(nonTransient)
  }

  //TODO: is it correct behaviour?
  private def retrieveMaxOffsetsOffsets(records: ConsumerRecords[Array[Byte], Array[Byte]]): Map[TopicPartition, OffsetAndMetadata] = {
    records.iterator().asScala.map {
      rec =>
        (new TopicPartition(rec.topic(), rec.partition()), rec.offset())
    }.toList.groupBy(_._1).mapValues(_.map(_._2).max).mapValues(new OffsetAndMetadata(_))
  }

  //Errors from this method will be considered as fatal, handled by uncaughtExceptionHandler and probably causing System.exit
  def close(): Unit = {
    List(producer, consumer)
      .filter(_ != null)
      .foreach(closeable => retryCloseOnInterrupt(closeable.close))
    logger.info(s"Closed runner for ${metaData.id}")
  }

  private def configSanityCheck(): Unit = {
    val properties = KafkaUtils.toPropertiesForConsumer(engineConfig.kafka, None)
    val maxPollInterval = new ConsumerConfig(properties).getInt(CommonClientConfigs.MAX_POLL_INTERVAL_MS_CONFIG)
    if (maxPollInterval <= (engineConfig.interpreterTimeout + engineConfig.publishTimeout).toMillis) {
      throw new IllegalArgumentException(s"publishTimeout + interpreterTimeout cannot exceed " +
        s"${CommonClientConfigs.MAX_POLL_INTERVAL_MS_CONFIG}")
    }
  }

  //it may happen that interrupt signal will be mixed in closing process. We want to close
  //normally, so we retry close action - but only once, as we expect only one interrupt call
  private def retryCloseOnInterrupt(action: () => Unit): Unit = {
    try {
      action()
    } catch {
      case _: InterruptedException | _: InterruptException  =>
        //This is important - as it's the only way to clear interrupted flag...
        val wasInterrupted = Thread.interrupted()
        logger.debug(s"Interrupted during close: $wasInterrupted, trying once more")
        action()
    }
  }

}
