package pl.touk.nussknacker.engine.baseengine.kafka

import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader.arbitraryTypeValueReader
import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.Interpreter.{FutureShape, InterpreterShape}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{JobData, StreamMetaData}
import pl.touk.nussknacker.engine.baseengine.ScenarioInterpreterFactory
import pl.touk.nussknacker.engine.baseengine.ScenarioInterpreterFactory.ScenarioInterpreterWithLifecycle
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.{BaseEngineRuntimeContext, EngineRuntimeContextPreparer}
import pl.touk.nussknacker.engine.baseengine.capabilities.FixedCapabilityTransformer
import pl.touk.nussknacker.engine.baseengine.kafka.KafkaTransactionalScenarioInterpreter.{EngineConfig, Output}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.exception.KafkaExceptionConsumerConfig
import shapeless.syntax.typeable.typeableOps

import java.lang.Thread.UncaughtExceptionHandler
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

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
object KafkaTransactionalScenarioInterpreter {

  type Output = ProducerRecord[Array[Byte], Array[Byte]]

  /*
    interpreterTimeout and publishTimeouts should be adjusted to fetch.max.bytes/max.poll.records
    shutdownTimeout should be longer then pollDuration
   */
  case class EngineConfig(pollDuration: FiniteDuration = 100 millis,
                          shutdownTimeout: Duration = 10 seconds,
                          interpreterTimeout: Duration = 10 seconds,
                          publishTimeout: Duration = 5 seconds,
                          exceptionHandlingConfig: KafkaExceptionConsumerConfig)

}

class KafkaTransactionalScenarioInterpreter(scenario: EspProcess,
                                            jobData: JobData,
                                            modelData: ModelData,
                                            engineRuntimeContextPreparer: EngineRuntimeContextPreparer,
                                            uncaughtExceptionHandler: UncaughtExceptionHandler
                                            )(implicit ec: ExecutionContext) extends AutoCloseable {

  private implicit val capability: FixedCapabilityTransformer[Future] = new FixedCapabilityTransformer[Future]()

  private implicit val shape: InterpreterShape[Future] = new FutureShape()

  private val interpreter: ScenarioInterpreterWithLifecycle[Future, Output] =
    ScenarioInterpreterFactory.createInterpreter[Future, Output](scenario, modelData)
      .fold(errors => throw new IllegalArgumentException(s"Failed to compile: $errors"), identity)

  private val context: BaseEngineRuntimeContext = engineRuntimeContextPreparer.prepare(jobData)

  private val engineConfig = modelData.processConfig.as[EngineConfig]

  private val taskRunner: TaskRunner = new TaskRunner(scenario.id, extractPoolSize(), createScenarioTaskRun , engineConfig.shutdownTimeout, uncaughtExceptionHandler)

  def run(): Unit = {
    interpreter.open(context)
    taskRunner.run()
  }

  def close(): Unit = {
    closeAllInFinally(List(taskRunner, context, interpreter))
  }

  private def closeAllInFinally(list: List[AutoCloseable]): Unit = list match {
    case Nil => ()
    case h::t => try {
      h.close()
    } finally {
      closeAllInFinally(t)
    }
  }

  private def extractPoolSize() = {
    scenario.metaData.typeSpecificData.cast[StreamMetaData].flatMap(_.parallelism).getOrElse(1)
  }

  //to override in tests...
  private[kafka] def createScenarioTaskRun(): Runnable with AutoCloseable = {
    new KafkaSingleScenarioTaskRun(scenario.metaData, context, engineConfig, modelData.processConfig, interpreter)
  }

}

