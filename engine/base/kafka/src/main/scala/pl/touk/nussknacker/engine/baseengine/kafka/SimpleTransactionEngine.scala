package pl.touk.nussknacker.engine.baseengine.kafka

import cats.implicits.toTraverseOps
import org.apache.kafka.clients.consumer.{ConsumerRecord, ConsumerRecords, KafkaConsumer, OffsetAndMetadata}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.TopicPartition
import pl.touk.nussknacker.engine.Interpreter.{FutureShape, InterpreterShape}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.engine.api.{Context, JobData, StreamMetaData}
import pl.touk.nussknacker.engine.baseengine.ScenarioInterpreterFactory
import pl.touk.nussknacker.engine.baseengine.ScenarioInterpreterFactory.ScenarioInterpreterWithLifecycle
import pl.touk.nussknacker.engine.baseengine.api.interpreterTypes.{ScenarioInputBatch, SourceId}
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.{EngineRuntimeContext, EngineRuntimeContextPreparer}
import pl.touk.nussknacker.engine.baseengine.capabilities.FixedCapabilityTransformer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils}
import shapeless.syntax.typeable.typeableOps

import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.CollectionConverters.{asScalaIteratorConverter, iterableAsScalaIterableConverter, mapAsJavaMapConverter}

class SimpleTransactionEngine(scenario: EspProcess,
                              jobData: JobData,
                              modelData: ModelData,
                              engineRuntimeContextPreparer: EngineRuntimeContextPreparer)(implicit ec: ExecutionContext) {

  private implicit val capability: FixedCapabilityTransformer[Future] = new FixedCapabilityTransformer[Future]()

  private implicit val shape: InterpreterShape[Future] = new FutureShape()

  private val interpreter: ScenarioInterpreterWithLifecycle[Future, ProducerRecord[Array[Byte], Array[Byte]]] =
    ScenarioInterpreterFactory.createInterpreter[Future, ProducerRecord[Array[Byte], Array[Byte]]](scenario, modelData)
    .getOrElse(throw new IllegalArgumentException("Failed to compile"))

  private val poolSize = scenario.metaData.typeSpecificData.cast[StreamMetaData].flatMap(_.parallelism).getOrElse(1)
  private val threadPool = Executors.newFixedThreadPool(poolSize)
  private val context: EngineRuntimeContext = engineRuntimeContextPreparer.prepare(scenario.id)

  private val kafkaConfig = KafkaConfig.parseConfig(modelData.processConfig)
  interpreter.open(jobData, context)
  val tasks = (0 to poolSize).map(_ => new LoopUntilClosed(new ScenarioTaskRun(scenario.id, kafkaConfig, context, interpreter)))
  tasks.foreach(threadPool.submit)

  def close(): Unit = {
    tasks.foreach(_.close())
    threadPool.shutdownNow()
    interpreter.close()
  }


}

class LoopUntilClosed(singleRun: Runnable) extends Runnable with AutoCloseable {

  private val closed = new AtomicBoolean(false)

  override def run(): Unit = {
    //TODO: handle errors
    while (!closed.get()) {
      singleRun.run()
    }
  }

  override def close(): Unit = {
    closed.set(true)
  }
}


class ScenarioTaskRun(scenarioId: String,
                      kafkaConfig: KafkaConfig,
                      engineRuntimeContext: EngineRuntimeContext,
                      interpreter: ScenarioInterpreterWithLifecycle[Future, ProducerRecord[Array[Byte], Array[Byte]]])(implicit ec:ExecutionContext) extends Runnable {

  private val groupId = scenarioId

  private val consumer = new KafkaConsumer[Array[Byte], Array[Byte]](KafkaUtils.toProperties(kafkaConfig, Some(groupId)))
  private val producer = new KafkaProducer[Array[Byte], Array[Byte]](KafkaUtils.toProducerProperties(kafkaConfig, groupId))

  {
    producer.initTransactions()
  }

  private val sourceToTopic: Map[String, Map[SourceId, CommonKafkaSource]] = interpreter.sources
    .mapValues(_.asInstanceOf[CommonKafkaSource]).toList.groupBy(_._2.topic).mapValues(_.toMap)

  def close(): Unit = {
    consumer.wakeup()
    producer.abortTransaction()
    producer.close()
  }

  def run(): Unit = {

    val records = consumer.poll(Duration.ZERO)

    val valuesToRun = sourceToTopic.toList.flatMap { case (topic, sources) =>
      val forTopic = records.records(topic).asScala.toList
      sources.mapValues(source => forTopic.map(source.deserializer(engineRuntimeContext, _))).toList.flatMap {
        case (sourceId, contexts) => contexts.map((sourceId, _))
      }
    }

    producer.beginTransaction()

    Await.result(interpreter.invoke(ScenarioInputBatch(valuesToRun)).flatMap { data =>
      val results = data.value.map(_.result)
      //TODO: val errors = data.written
      results.map(KafkaUtils.sendToKafka(_)(producer)).sequence
    }, 10 seconds)

    val offsetsMap: Map[TopicPartition, OffsetAndMetadata] = retrieveOffsets(records)

    producer.sendOffsetsToTransaction(offsetsMap.asJava, groupId)
    producer.commitTransaction()


  }

  private def retrieveOffsets(records: ConsumerRecords[Array[Byte], Array[Byte]]) = {
    records.iterator().asScala.map { rec =>
      (new TopicPartition(rec.topic(), rec.partition()), rec.offset())
    }.toList.groupBy(_._1).mapValues(_.map(_._2).max).mapValues(new OffsetAndMetadata(_))
  }
}


trait CommonKafkaSource extends Source[AnyRef] {

  def topic: String

  def deserializer: (EngineRuntimeContext, ConsumerRecord[Array[Byte], Array[Byte]]) => Context

}


