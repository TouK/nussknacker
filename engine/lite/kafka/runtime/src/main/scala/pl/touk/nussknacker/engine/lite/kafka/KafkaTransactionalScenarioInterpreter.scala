package pl.touk.nussknacker.engine.lite.kafka

import org.apache.pekko.http.scaladsl.server.Route
import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.Interpreter.FutureShape
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.exception.KafkaExceptionConsumerConfig
import pl.touk.nussknacker.engine.lite.ScenarioInterpreterFactory.ScenarioInterpreterWithLifecycle
import pl.touk.nussknacker.engine.lite.TaskStatus.TaskStatus
import pl.touk.nussknacker.engine.lite.TestRunner._
import pl.touk.nussknacker.engine.lite.api.runtimecontext.{LiteEngineRuntimeContext, LiteEngineRuntimeContextPreparer}
import pl.touk.nussknacker.engine.lite.capabilities.FixedCapabilityTransformer
import pl.touk.nussknacker.engine.lite.kafka.KafkaTransactionalScenarioInterpreter.{Input, Output}
import pl.touk.nussknacker.engine.lite.metrics.SourceMetrics
import pl.touk.nussknacker.engine.lite.{
  InterpreterTestRunner,
  RunnableScenarioInterpreter,
  ScenarioInterpreterFactory,
  TestRunner
}
import pl.touk.nussknacker.engine.util.ExecutionContextWithIORuntimeAdapter

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Using

/*
  V. simple engine running kafka->scenario->kafka use case
  Assumptions:
  - only one Kafka cluster
  - consume->process->producer loop in handled transactionally
  - errors are sent to error topic
  - Future is used as effect, messages to be sent are handled via interpreter scenario Result
  - There is one consumer and producer per task unit in scenario. We could have one per source, but this way it's a bit simpler,
    what's more it can be possible to share same Consumer/Producer between many processes and further reduce resource usage

    Different possibilities:
    - Each source handles own runner-thread - more like in Flink, pros: can be extended to arbitrary sources, cons: complex, not easy to handle transactions
    - Each source is responsible for poll() invocations. pros: can be extended to periodic sources etc, cons: complex, more resources needed, a bit more difficult to handle transactions
    - Produced records in effect monad instead of result. pros: ? cons: ? :)
 */
object KafkaTransactionalScenarioInterpreter {

  type Input = ConsumerRecord[Array[Byte], Array[Byte]]

  type Output = ProducerRecord[Array[Byte], Array[Byte]]

  /*
    interpreterTimeout and publishTimeouts should be adjusted to fetch.max.bytes/max.poll.records
    shutdownTimeout should be longer then pollDuration
   */
  case class KafkaInterpreterConfig(
      pollDuration: FiniteDuration = 100 millis,
      shutdownTimeout: Duration = 10 seconds,
      interpreterTimeout: Duration = 10 seconds,
      publishTimeout: Duration = 5 seconds,
      waitAfterFailureDelay: FiniteDuration = 10 seconds,
      kafka: KafkaConfig,
      exceptionHandlingConfig: KafkaExceptionConsumerConfig,
      kafkaTransactionsEnabled: Option[Boolean] = None
  )

  private[kafka] implicit val capability: FixedCapabilityTransformer[Future] = new FixedCapabilityTransformer[Future]()

  def testRunner(implicit ec: ExecutionContext): TestRunner = new InterpreterTestRunner[Future, Input, AnyRef]

  def apply(
      scenario: CanonicalProcess,
      jobData: JobData,
      liteKafkaJobData: LiteKafkaJobData,
      modelData: ModelData,
      engineRuntimeContextPreparer: LiteEngineRuntimeContextPreparer
  )(implicit ec: ExecutionContext): KafkaTransactionalScenarioInterpreter = {
    val interpreter = ScenarioInterpreterFactory
      .createInterpreter[Future, Input, Output](scenario, jobData, modelData)
      .valueOr(errors => throw new IllegalArgumentException(s"Failed to compile: $errors"))
    new KafkaTransactionalScenarioInterpreter(
      interpreter,
      scenario,
      jobData,
      liteKafkaJobData,
      modelData,
      engineRuntimeContextPreparer
    )
  }

}

class KafkaTransactionalScenarioInterpreter private[kafka] (
    interpreter: ScenarioInterpreterWithLifecycle[Future, Input, Output],
    scenario: CanonicalProcess,
    jobData: JobData,
    liteKafkaJobData: LiteKafkaJobData,
    modelData: ModelData,
    engineRuntimeContextPreparer: LiteEngineRuntimeContextPreparer
)(implicit ec: ExecutionContext)
    extends RunnableScenarioInterpreter
    with LazyLogging {

  override def status(): TaskStatus = taskRunner.status()

  import KafkaTransactionalScenarioInterpreter._
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._

  private val context: LiteEngineRuntimeContext = engineRuntimeContextPreparer.prepare(jobData)

  private val sourceMetrics = new SourceMetrics(interpreter.sources.keys)

  private val interpreterConfig = modelData.modelConfig.as[KafkaInterpreterConfig]

  private val taskRunner: TaskRunner = new TaskRunner(
    scenario.name.value,
    liteKafkaJobData.tasksCount,
    createScenarioTaskRun,
    interpreterConfig.shutdownTimeout,
    interpreterConfig.waitAfterFailureDelay,
    context.metricsProvider
  )

  override def run(): IO[Unit] = {
    for {
      _ <- IO.delay(sourceMetrics.registerOwnMetrics(context.metricsProvider))
      _ <- IO.delay(interpreter.open(context))
      _ <- ExecutionContextWithIORuntimeAdapter
        .createFrom(ec)
        .use { adapter =>
          IO.delay {
            taskRunner
              .run(adapter)
              .unsafeRunAsync {
                case Left(ex) =>
                  logger.error("Task runner failed", ex)
                case Right(_) =>
              }(adapter.ioRuntime)
          }
        }
    } yield ()
  }

  override def close(): Unit = {
    Using.resources(context, interpreter, taskRunner)((_, _, _) => ()) // empty "using" to ensure correct closing
  }

  // to override in tests...
  private[kafka] def createScenarioTaskRun(taskId: String): Task = {
    new KafkaSingleScenarioTaskRun(
      taskId,
      scenario.metaData,
      context,
      interpreterConfig,
      interpreter,
      sourceMetrics,
      modelData.namingStrategy
    )
  }

  override def routes: Option[Route] = None
}
