package pl.touk.nussknacker.engine.embedded

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.StateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.{JobData, LiteStreamMetaData, ProcessVersion}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.TestRunner
import pl.touk.nussknacker.engine.lite.kafka.{KafkaTransactionalScenarioInterpreter, LiteKafkaJobData, TaskStatus}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class StreamingDeploymentStrategy extends DeploymentStrategy with LazyLogging {

  override type ScenarioInterpreter = StreamingDeployment

  override def close(): Unit = {}

  protected def handleUnexpectedError(version: ProcessVersion, throwable: Throwable): Unit = {
    logger.error(s"Scenario: $version failed unexpectedly", throwable)
  }

  override def onScenarioAdded(jobData: JobData,
                               parsedResolvedScenario: EspProcess)(implicit ec: ExecutionContext): Try[StreamingDeployment] = {


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
        _ => Success(new StreamingDeployment(interpreter)),
        ex => {
          interpreter.close()
          Failure(ex)
        })
    }

  }

  override def testRunner(implicit ec: ExecutionContext): TestRunner = KafkaTransactionalScenarioInterpreter.testRunner

  class StreamingDeployment(interpreter: KafkaTransactionalScenarioInterpreter) extends Deployment {

    override def readStatus(): StateStatus = interpreter.status() match {
      case TaskStatus.Running => SimpleStateStatus.Running
      case TaskStatus.DuringDeploy => SimpleStateStatus.DuringDeploy
      case TaskStatus.Restarting => EmbeddedStateStatus.Restarting
      case other => throw new IllegalStateException(s"Not supporter task status: $other")
    }

    override def close(): Unit = {
      interpreter.close()
    }

  }

}

