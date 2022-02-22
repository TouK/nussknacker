package pl.touk.nussknacker.engine.embedded

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.StateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.{JobData, LiteStreamMetaData, ProcessVersion}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.TestRunner
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.kafka.{KafkaTransactionalScenarioInterpreter, LiteKafkaJobData, TaskStatus}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class StreamingDeploymentStrategy extends DeploymentStrategy with LazyLogging {

  override type ScenarioInterpreter = KafkaTransactionalScenarioInterpreter

  override def open(): Unit = {}

  override def close(): Unit = {}

  protected def handleUnexpectedError(version: ProcessVersion, throwable: Throwable): Unit = {
    logger.error(s"Scenario: $version failed unexpectedly", throwable)
  }

  override def onScenarioAdded(jobData: JobData,
                               modelData: ModelData,
                               parsedResolvedScenario: EspProcess,
                               contextPreparer: LiteEngineRuntimeContextPreparer)(implicit ec: ExecutionContext): Try[KafkaTransactionalScenarioInterpreter] = {


    // TODO think about some better strategy for determining tasksCount instead of picking just parallelism for that
    val liteKafkaJobData = LiteKafkaJobData(tasksCount = parsedResolvedScenario.metaData.typeSpecificData.asInstanceOf[LiteStreamMetaData].parallelism.getOrElse(1))
    val interpreterTry = Try(KafkaTransactionalScenarioInterpreter(parsedResolvedScenario, jobData, liteKafkaJobData, modelData, contextPreparer))
    interpreterTry.flatMap { interpreter =>
      val runTry = Try {
        val result = interpreter.run()
        result.onComplete {
          case Failure(exception) => handleUnexpectedError(jobData.processVersion, exception)
          case Success(_) => //closed without problems
        }
      }
      runTry.transform(
        _ => Success(interpreter),
        ex => {
          interpreter.close()
          Failure(ex)
        })
    }

  }

  override def onScenarioCancelled(data: KafkaTransactionalScenarioInterpreter): Unit = data.close()

  override def readStatus(data: KafkaTransactionalScenarioInterpreter): StateStatus = data.status() match {
    case TaskStatus.Running => SimpleStateStatus.Running
    case TaskStatus.DuringDeploy => SimpleStateStatus.DuringDeploy
    case TaskStatus.Restarting => EmbeddedStateStatus.Restarting
    case other => throw new IllegalStateException(s"Not supporter task status: $other")
  }

  override def testRunner(implicit ec: ExecutionContext): TestRunner = KafkaTransactionalScenarioInterpreter.testRunner
}
