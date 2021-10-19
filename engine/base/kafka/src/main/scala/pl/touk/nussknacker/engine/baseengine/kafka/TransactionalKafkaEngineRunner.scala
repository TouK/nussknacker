package pl.touk.nussknacker.engine.baseengine.kafka

import cats.data.ReaderT
import cats.{Monad, MonadError}
import cats.implicits._
import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer, OffsetAndMetadata}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.TopicPartition
import pl.touk.nussknacker.engine.Interpreter.{FutureShape, InterpreterShape}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.engine.baseengine.ScenarioInterpreterFactory.ScenarioInterpreterWithLifecycle
import pl.touk.nussknacker.engine.baseengine.api.customComponentTypes.{BaseEngineSink, CapabilityTransformer, CustomComponentContext}
import pl.touk.nussknacker.engine.baseengine.api.interpreterTypes.{ScenarioInputBatch, SourceId}
import pl.touk.nussknacker.engine.baseengine.kafka.TransactionalKafkaEngineRunner.{CommonKafkaSource, Result, SingleKafkaSource, groupId, properties, run}
import pl.touk.nussknacker.engine.kafka.KafkaUtils

import java.time.Duration
import java.util.Properties
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.CollectionConverters.{asJavaCollectionConverter, asScalaIteratorConverter, iterableAsScalaIterableConverter, mapAsJavaMapConverter}
import scala.language.higherKinds

object TransactionalKafkaEngineRunner {

  type Result = List[ProducerRecord[Array[Byte], Array[Byte]]]

  type Offsets = Map[TopicPartition, Long]

  trait SingleKafkaSource extends Source[AnyRef] {

    def open(initialOffsets: Offsets): Unit

    def poll(): (Offsets, List[Context])

    def close(): Unit

  }

  trait CommonKafkaSource extends Source[AnyRef] {

    def topic: String

    def deserializer: ConsumerRecord[Array[Byte], Array[Byte]] => Context

  }

  trait KafkaSink extends BaseEngineSink[Result]

  //parsing scenario etc.
  private val run: ScenarioInterpreterWithLifecycle[Future, Result] = ???

  //config
  private val  properties: Properties = new Properties()
  private val groupId = "toSet"

  import ExecutionContext.Implicits.global

  implicit val monad: MonadError[Future, Throwable] = new FutureShape().monadError

  /*
    Common subscriber, less resources,
   */
  def runWithCommonSubscriber(): Unit = {
    val sourceToTopic: Map[String, Map[SourceId, CommonKafkaSource]] = run.sources
      .mapValues(_.asInstanceOf[CommonKafkaSource]).toList.groupBy(_._2.topic).mapValues(_.toMap)

    val consumer = new KafkaConsumer[Array[Byte], Array[Byte]](properties)
    val producer = new KafkaProducer[Array[Byte], Array[Byte]](properties)
    producer.initTransactions()

    consumer.subscribe(sourceToTopic.keys.asJavaCollection)
    while (true) {
      val records = consumer.poll(Duration.ZERO)

      val valuesToRun = sourceToTopic.toList.flatMap { case (topic, sources) =>
        val forTopic = records.records(topic).asScala.toList
        sources.mapValues(source => forTopic.map(source.deserializer)).toList.flatMap {
          case (sourceId, contexts) => contexts.map((sourceId, _))
        }
      }

      producer.beginTransaction()

      Await.result(run.invoke(ScenarioInputBatch(valuesToRun)).flatMap { data =>
        data.run._2.flatMap(_.result).map(k => KafkaUtils.sendToKafka(k)(producer)).sequence
      }, 10 seconds)

      val offsetsMap = records.iterator().asScala.map { rec =>
        (new TopicPartition(rec.topic(), rec.partition()), rec.offset())
      }.toList.groupBy(_._1).mapValues(_.map(_._2).max).mapValues(new OffsetAndMetadata(_))

      producer.sendOffsetsToTransaction(offsetsMap.asJava, groupId)
      producer.commitTransaction()

    }

  }

  def runWithSeparateSubscribers(): Unit = {

    val sourceToTopic: Map[SourceId, SingleKafkaSource] = run.sources
      .mapValues(_.asInstanceOf[SingleKafkaSource])

    sourceToTopic.foreach(_._2.open(null))

    val producer = new KafkaProducer[Array[Byte], Array[Byte]](properties)
    producer.initTransactions()

    while (true) {

      val (offsets, values) = sourceToTopic.map { case (sourceId, source) =>
        val (offsets, records) = source.poll()
        (offsets, records.map((sourceId, _)))
      }.toList.unzip

      producer.beginTransaction()      
      Await.result(run.invoke(ScenarioInputBatch(values.flatten)).flatMap { data =>
        data.run._2.flatMap(_.result).map(k => KafkaUtils.sendToKafka(k)(producer)).sequence
      }, 10 seconds)

      val offsetsMap = offsets.flatMap(_.toList)
        .groupBy(_._1).mapValues(offsets => new OffsetAndMetadata(offsets.map(_._2).max))

      producer.sendOffsetsToTransaction(offsetsMap.asJava, groupId)
      producer.commitTransaction()
    }
  }

}

//TODO:??
object AtLeastOnceKafkaEngineRunner {

  trait AtLeastSource extends Source[AnyRef] {

    def start(runner: List[Context] => Future[Unit]): Unit

    def close(): Unit

  }

  //producing results here?
  trait AtLeastSink extends BaseEngineSink[AnyRef]

}

object PollingScenarioEngine {


  trait PollingSource extends Source[AnyRef] {

    //TODO: czy chcemy przekazywac offsety?
    def open(): Unit

    def poll[F[_]:Monad](capabilitiesTransformer: CapabilityTransformer[F]): F[List[Context]]

    def close(): Unit

  }

  //parsing scenario etc.
  private val run: ScenarioInterpreterWithLifecycle[Future, Result] = ???

  //config
  private val  properties: Properties = new Properties()
  private val groupId = "toSet"

  import ExecutionContext.Implicits.global
  implicit val monad: MonadError[Future, Throwable] = new FutureShape().monadError



  /*
  def runWithSeparateSubscribers[F[_]]( capabilityTransformer: CapabilityTransformer[F],
                                        runner: ScenarioInterpreterWithLifecycle[F, Unit],
                                        run: F[Unit] => Unit): Unit = {

    val pollingSources: Map[SourceId, PollingSource] = runner.sources
      .mapValues(_.asInstanceOf[PollingSource])

    pollingSources.foreach(_._2.open())

    //val producer = new KafkaProducer[Array[Byte], Array[Byte]](properties)
    //producer.initTransactions()

    while (true) {

      val (offsets, values) = pollingSources.map { case (sourceId, source) =>
        val ctx = new
        val (offsets, records) = source.poll(capabilityTransformer)
        (offsets, records.map((sourceId, _)))
      }.toList.unzip

      producer.beginTransaction()
      Await.result(run.invoke(ScenarioInputBatch(values.flatten)).flatMap { data =>
        data.run._2.flatMap(_.result).map(k => KafkaUtils.sendToKafka(k)(producer)).sequence
      }, 10 seconds)

      val offsetsMap = offsets.flatMap(_.toList)
        .groupBy(_._1).mapValues(offsets => new OffsetAndMetadata(offsets.map(_._2).max))

      producer.sendOffsetsToTransaction(offsetsMap.asJava, groupId)
      producer.commitTransaction()
    }
  } */


  import ExecutionContext.Implicits.global
  type WithKafkaProducer[T] = ReaderT[Future, KafkaProducer[Array[Byte], Array[Byte]], T]

  //implicitly[Monad[WithKafkaProducer]].pure("").run()


}