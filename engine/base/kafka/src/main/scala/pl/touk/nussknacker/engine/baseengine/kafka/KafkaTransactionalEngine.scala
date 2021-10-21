package pl.touk.nussknacker.engine.baseengine.kafka

import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader.arbitraryTypeValueReader
import org.apache.kafka.clients.consumer.{ConsumerRecords, KafkaConsumer, OffsetAndMetadata}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.TopicPartition
import pl.touk.nussknacker.engine.Interpreter.{FutureShape, InterpreterShape}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{Context, JobData, StreamMetaData}
import pl.touk.nussknacker.engine.baseengine.ScenarioInterpreterFactory
import pl.touk.nussknacker.engine.baseengine.ScenarioInterpreterFactory.ScenarioInterpreterWithLifecycle
import pl.touk.nussknacker.engine.baseengine.api.commonTypes.{ErrorType, ResultType}
import pl.touk.nussknacker.engine.baseengine.api.interpreterTypes
import pl.touk.nussknacker.engine.baseengine.api.interpreterTypes.{ScenarioInputBatch, SourceId}
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.{EngineRuntimeContext, EngineRuntimeContextPreparer}
import pl.touk.nussknacker.engine.baseengine.capabilities.FixedCapabilityTransformer
import pl.touk.nussknacker.engine.baseengine.kafka.KafkaTransactionalEngine.{EngineConfig, Output}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.exception.{KafkaExceptionConsumerConfig, KafkaJsonExceptionSerializationSchema}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils}
import pl.touk.nussknacker.engine.util.exception.WithExceptionExtractor
import shapeless.syntax.typeable.typeableOps

import java.util.UUID
import scala.compat.java8.DurationConverters.FiniteDurationops
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.CollectionConverters.{asScalaIteratorConverter, iterableAsScalaIterableConverter, mapAsJavaMapConverter, setAsJavaSetConverter}

/*
  V. simple engine running kafka->scenario->kafka use case
  Assumptions:
  - only one Kafka cluster
  - consume->process->producer loop in handled transactionally
  - errors are sent to error topic
  - Future is used as effect, messages to be sent are handled via baseengine scenario Result
  - There is one consumer and producer per parallelism unit in scenario. We could have one per source, but this way it's a bit simpler,
    what's more it can be possible to share same Consumer/Producer between many processes and further reduce resource usage

    Different possibilities:
    - Each source handles own runner-thread - more like in Flink, pros: can be extended to arbitrary sources, cons: complex, not easy to handle transactions
    - Each source is responsible for poll() invoctions. pros: can be extended to periodic sources etc, cons: complex, more resources needed, a bit more difficult to handle transactions
    - Produced records in effect monad instead of result. pros: ? cons: ? :)
 */
object KafkaTransactionalEngine {

  type Output = ProducerRecord[Array[Byte], Array[Byte]]

  case class EngineConfig(pollDuration: FiniteDuration = 100 millis,
                          timeout: Duration = 10 seconds,
                          exceptionHandlingConfig: KafkaExceptionConsumerConfig)

}

class KafkaTransactionalEngine(scenario: EspProcess,
                               jobData: JobData,
                               modelData: ModelData,
                               engineRuntimeContextPreparer: EngineRuntimeContextPreparer)(implicit ec: ExecutionContext) extends AutoCloseable {

  private implicit val capability: FixedCapabilityTransformer[Future] = new FixedCapabilityTransformer[Future]()

  private implicit val shape: InterpreterShape[Future] = new FutureShape()

  private val interpreter: ScenarioInterpreterWithLifecycle[Future, ProducerRecord[Array[Byte], Array[Byte]]] =
    ScenarioInterpreterFactory.createInterpreter[Future, Output](scenario, modelData)
      .fold(errors => throw new IllegalArgumentException(s"Failed to compile: $errors"), identity)

  private val context: EngineRuntimeContext = engineRuntimeContextPreparer.prepare(scenario.id)

  private val engineConfig = modelData.processConfig.as[EngineConfig]

  private val taskRunner = new TaskRunner(extractPoolSize(), () => new ScenarioTaskRun(), engineConfig.timeout)

  interpreter.open(jobData, context)

  def close(): Unit = {
    taskRunner.close()
    interpreter.close()
  }

  private def extractPoolSize() = {
    scenario.metaData.typeSpecificData.cast[StreamMetaData].flatMap(_.parallelism).getOrElse(1)
  }

  class ScenarioTaskRun extends LazyLogging with Runnable with AutoCloseable {

    private val groupId = scenario.metaData.id

    private val kafkaConfig = KafkaConfig.parseConfig(modelData.processConfig)

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
      producer.initTransactions()
      consumer.subscribe(sourceToTopic.keys.toSet.asJava)
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

      val valuesToRun = parseRecords(records)
      //we process batch, assuming that no side effects appear here
      val output = Await.result(interpreter.invoke(ScenarioInputBatch(valuesToRun)), engineConfig.timeout)

      //we send all results - offsets, errors and results in one transactions
      producer.beginTransaction()
      val offsetsMap: Map[TopicPartition, OffsetAndMetadata] = retrieveMaxOffsetsOffsets(records)
      producer.sendOffsetsToTransaction(offsetsMap.asJava, groupId)
      val sendFuture = sendOutputToKafka(output)
      Await.result(sendFuture, engineConfig.timeout)
      producer.commitTransaction()
    }


    private def parseRecords(records: ConsumerRecords[Array[Byte], Array[Byte]]): List[(SourceId, Context)] = {
      sourceToTopic.toList.flatMap {
        case (topic, sources) =>
          val forTopic = records.records(topic).asScala.toList
          sources.mapValues(source => forTopic.map(source.deserializer(context, _))).toList.flatMap {
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
      KafkaJsonExceptionSerializationSchema(scenario.metaData, engineConfig.exceptionHandlingConfig).serialize(nonTransient)
    }

    //TODO: is it correct behaviour?
    private def retrieveMaxOffsetsOffsets(records: ConsumerRecords[Array[Byte], Array[Byte]]): Map[TopicPartition, OffsetAndMetadata] = {
      records.iterator().asScala.map {
        rec =>
          (new TopicPartition(rec.topic(), rec.partition()), rec.offset())
      }.toList.groupBy(_._1).mapValues(_.map(_._2).max).mapValues(new OffsetAndMetadata(_))
    }

    def close(): Unit = {
      consumer.wakeup()
      producer.close()
    }

  }

}




