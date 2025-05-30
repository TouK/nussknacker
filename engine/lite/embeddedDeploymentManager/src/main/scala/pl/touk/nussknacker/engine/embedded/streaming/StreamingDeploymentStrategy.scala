package pl.touk.nussknacker.engine.embedded.streaming

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData.BaseModelDataExt
import pl.touk.nussknacker.engine.api.{JobData, LiteStreamMetaData, ProcessVersion}
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.deployment.DeploymentStatus
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.embedded.{Deployment, DeploymentStrategy}
import pl.touk.nussknacker.engine.lite.TaskStatus
import pl.touk.nussknacker.engine.lite.kafka.{KafkaTransactionalScenarioInterpreter, LiteKafkaJobData}
import pl.touk.nussknacker.engine.util.ExecutionContextWithIORuntimeAdapter

import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class StreamingDeploymentStrategy extends DeploymentStrategy with LazyLogging {

  override def close(): Unit = {}

  protected def handleUnexpectedError(version: ProcessVersion, throwable: Throwable): Unit = {
    logger.error(s"Scenario: $version failed unexpectedly", throwable)
  }

  override def onScenarioAdded(
      jobData: JobData,
      nodesDeploymentData: NodesDeploymentData,
      parsedResolvedScenario: CanonicalProcess
  )(
      implicit ec: ExecutionContext
  ): Try[StreamingDeployment] = {
    val liteKafkaJobData = LiteKafkaJobData(
      // TODO think about some better strategy for determining tasksCount instead of picking just parallelism for that
      tasksCount =
        parsedResolvedScenario.metaData.typeSpecificData.asInstanceOf[LiteStreamMetaData].parallelism.getOrElse(1)
    )
    val interpreterTry = Try(
      KafkaTransactionalScenarioInterpreter(
        modelDataProvider.getCurrentModelData().asInvokableModelData,
        contextPreparer,
        parsedResolvedScenario,
        jobData,
        nodesDeploymentData,
        liteKafkaJobData,
      )
    )
    interpreterTry.flatMap { interpreter =>
      val ecWithRuntime = ExecutionContextWithIORuntimeAdapter.unsafeCreateFrom(ec)
      val runTry = Try {
        interpreter
          .run()
          .handleErrorWith { exception =>
            handleUnexpectedError(jobData.processVersion, exception)
            interpreter.close()
            IO.raiseError(exception)
          }
          .unsafeRunSync()(ecWithRuntime.ioRuntime)
      }
      runTry.transform(
        _ => {
          ecWithRuntime.close()
          Success(new StreamingDeployment(interpreter, jobData.processVersion.versionId, Instant.now()))
        },
        ex => {
          ecWithRuntime.close()
          Failure(ex)
        }
      )
    }
  }

  class StreamingDeployment(interpreter: KafkaTransactionalScenarioInterpreter, version: VersionId, startedAt: Instant)
      extends Deployment {

    override def status(): DeploymentStatus = interpreter.status() match {
      case TaskStatus.Running      => DeploymentStatus.Running(version, startedAt)
      case TaskStatus.DuringDeploy => DeploymentStatus.DuringDeploy(version)
      case TaskStatus.Restarting   => DeploymentStatus.Restarting
      case other                   => throw new IllegalStateException(s"Not supporter task status: $other")
    }

    override def close(): Unit = {
      interpreter.close()
    }

  }

}
