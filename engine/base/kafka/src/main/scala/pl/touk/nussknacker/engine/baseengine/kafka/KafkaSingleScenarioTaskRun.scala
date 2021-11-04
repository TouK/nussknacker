package pl.touk.nussknacker.engine.baseengine.kafka

import cats.implicits.toTraverseOps
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecords, KafkaConsumer, OffsetAndMetadata}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.TopicPartition
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.{Context, MetaData}
import pl.touk.nussknacker.engine.baseengine.ScenarioInterpreterFactory.ScenarioInterpreterWithLifecycle
import pl.touk.nussknacker.engine.baseengine.api.commonTypes.{ErrorType, ResultType}
import pl.touk.nussknacker.engine.baseengine.api.interpreterTypes
import pl.touk.nussknacker.engine.baseengine.api.interpreterTypes.{ScenarioInputBatch, SourceId}
import pl.touk.nussknacker.engine.baseengine.kafka.KafkaTransactionalScenarioInterpreter.{EngineConfig, Output}
import pl.touk.nussknacker.engine.kafka.exception.KafkaJsonExceptionSerializationSchema
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils}
import pl.touk.nussknacker.engine.util.exception.WithExceptionExtractor

import java.util.UUID
import scala.compat.java8.DurationConverters.FiniteDurationops
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.CollectionConverters.{asJavaCollectionConverter, asScalaIteratorConverter, iterableAsScalaIterableConverter, mapAsJavaMapConverter}

class KafkaSingleScenarioTaskRun(metaData: MetaData,
                                 context: EngineRuntimeContext,
                                 engineConfig: EngineConfig,
                                 processConfig: Config,
                                 interpreter: ScenarioInterpreterWithLifecycle[Future, Output])
                                (implicit ec: ExecutionContext) extends LazyLogging with Runnable with AutoCloseable {

  private val groupId = metaData.id

  private val kafkaConfig = KafkaConfig.parseConfig(processConfig)

  private val producerProps = KafkaUtils.toProducerProperties(kafkaConfig, groupId)
  //FIXME generate correct id - how to connect to topic/partition??
  producerProps.put("transactional.id", groupId + UUID.randomUUID().toString)

  private val consumer = new KafkaConsumer[Array[Byte], Array[Byte]](KafkaUtils.toPropertiesForConsumer(kafkaConfig, Some(groupId)))
  private val producer = new KafkaProducer[Array[Byte], Array[Byte]](producerProps)

  private val sourceToTopic: Map[String, Map[SourceId, CommonKafkaSource]] = interpreter.sources.flatMap {
    case (sourceId, kafkaSource: CommonKafkaSource) =>
      kafkaSource.topics.map(topic => topic -> (sourceId, kafkaSource))
    case (sourceId, other) => throw new IllegalArgumentException(s"Unexpected source: $other for ${sourceId.value}")
  }.groupBy(_._1).mapValues(_.values.toMap)

  {
    configSanityCheck()
    producer.initTransactions()
    consumer.subscribe(sourceToTopic.keys.toSet.asJavaCollection)
  }

  //We process all records, wait for outputs and only then send results in trsnaction
  //This way we trade latency (because first event in batch has to wait for all other)
  //for short transaction (we start only when all external invocations etc. are completed)
  def run(): Unit = {

    val records = consumer.poll(engineConfig.pollDuration.toJava)
    if (records.isEmpty) {
      logger.debug("No records, skipping")
      return
    }

    val valuesToRun = deserializeRecords(records)
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


  private def deserializeRecords(records: ConsumerRecords[Array[Byte], Array[Byte]]): List[(SourceId, Context)] = {
    sourceToTopic.toList.flatMap {
      case (topic, sources) =>
        val forTopic = records.records(topic).asScala.toList
        sources.mapValues(source => forTopic.map(source.deserialize(context, _))).toList.flatMap {
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
    consumer.close()
    producer.close()
    logger.info(s"Closed runner for ${metaData.id}")
  }

  private def configSanityCheck(): Unit = {
    val properties = KafkaUtils.toPropertiesForConsumer(kafkaConfig, Some("dummy"))
    val maxPollInterval = new ConsumerConfig(properties).getInt(CommonClientConfigs.MAX_POLL_INTERVAL_MS_CONFIG)
    if (maxPollInterval <= (engineConfig.interpreterTimeout + engineConfig.publishTimeout).toMillis) {
      throw new IllegalArgumentException(s"publishTimeout + interpreterTimeout cannot exceed " +
        s"${CommonClientConfigs.MAX_POLL_INTERVAL_MS_CONFIG}")
    }
  }


}
