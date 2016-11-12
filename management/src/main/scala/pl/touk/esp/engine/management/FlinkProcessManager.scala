package pl.touk.esp.engine.management

import java.io.File
import java.util.concurrent.{TimeUnit, TimeoutException}

import akka.pattern.AskTimeoutException
import com.typesafe.config.{Config, ConfigValueType}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.{JobID, JobSubmissionResult}
import org.apache.flink.client.program.{PackagedProgram, ProgramInvocationException, StandaloneClusterClient}
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.akka.AkkaUtils
import org.apache.flink.runtime.client.{JobClient, JobTimeoutException}
import org.apache.flink.runtime.instance.ActorGateway
import org.apache.flink.runtime.messages.JobManagerMessages
import org.apache.flink.runtime.messages.JobManagerMessages._
import org.apache.flink.runtime.util.LeaderRetrievalUtils
import pl.touk.esp.engine.api.deployment._
import pl.touk.esp.engine.marshall.ProcessMarshaller

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.Try

object FlinkProcessManager {
  def apply(config: Config): FlinkProcessManager = {
    val flinkConf = config.getConfig("flinkConfig")

    val clientConfig = new Configuration()
    flinkConf.entrySet().toList.foreach { entry =>
      val key = entry.getKey
      entry.getValue.valueType() match {
        case ConfigValueType.BOOLEAN => clientConfig.setBoolean(key, flinkConf.getBoolean(key))
        case ConfigValueType.NUMBER => clientConfig.setLong(key, flinkConf.getLong(key))
        case ConfigValueType.STRING => clientConfig.setString(key, flinkConf.getString(key))
        case _ =>
      }
    }
    val timeout = flinkConf.getDuration("jobManagerTimeout", TimeUnit.MILLISECONDS) millis

    new FlinkProcessManager(config, new RestartableFlinkGateway(() => new DefaultFlinkGateway(clientConfig, timeout)))
  }


}


class FlinkProcessManager(config: Config,
                          gateway: FlinkGateway) extends ProcessManager with LazyLogging {

  //tyle nam wystarczy, z aktorami to nigdy nic nie wiadomo...
  implicit val ec = ExecutionContext.Implicits.global

  private val flinkConf = config.getConfig("flinkConfig")

  private def prepareProgram(processId: String, processDeploymentData: ProcessDeploymentData) : PackagedProgram = {
    val jarFile = new File(flinkConf.getString("jarPath"))
    val configPart = extractProcessConfig

    processDeploymentData match {
      case GraphProcess(processAsJson) =>
        new PackagedProgram(jarFile, "pl.touk.esp.engine.process.FlinkProcessMain", List(processAsJson, configPart):_*)
      case CustomProcess(mainClass) =>
        new PackagedProgram(jarFile, mainClass, List(processId, configPart): _*)
    }
  }


  override def deploy(processId: String, processDeploymentData: ProcessDeploymentData) = {
    val program = prepareProgram(processId, processDeploymentData)

    import cats.data.OptionT
    import cats.implicits._

    val stoppingResult = for {
      maybeOldJob <- OptionT(findJobStatus(processId))
      maybeSavePoint <- {
        { logger.debug(s"Deploying $processId. Status: $maybeOldJob") }
        OptionT.liftF(stopSavingSavepoint(maybeOldJob))
      }
    } yield {
      logger.debug(s"Deploying $processId. Saving savepoint finished")
      maybeSavePoint
    }

    stoppingResult.value.map { maybeSavepoint =>
      maybeSavepoint.foreach(program.setSavepointPath)
      logger.debug(s"Deploying $processId. Setting savepoint finished")
      Try(gateway.run(program)).recover {
        //TODO: jest blad we flink, future nie dostaje odpowiedzi jak poleci wyjatek przy savepoincie :|
        case e:ProgramInvocationException if e.getCause.isInstanceOf[JobTimeoutException] && maybeSavepoint.isDefined =>
          program.setSavepointPath(null)
          logger.info(s"Failed to run $processId with savepoint, trying with empty state")
          gateway.run(program)
      }.get
    }
  }

  private def stopSavingSavepoint(job: ProcessState): Future[String] = {
    val jobId = JobID.fromHexString(job.id)
    for {
      savepointPath <- makeSavepoint(jobId)
      _ <- cancel(jobId)
    } yield savepointPath
  }

  private def cancel(jobId: JobID) = {
    gateway.invokeJobManager[CancellationSuccess](CancelJob(jobId)).map(_ => ())
  }

  private def makeSavepoint(jobId: JobID): Future[String] = {
    gateway.invokeJobManager[Any](JobManagerMessages.TriggerSavepoint(jobId)).map {
      case TriggerSavepointSuccess(_, path) => path
      case TriggerSavepointFailure(_, reason) => throw reason
    }
  }

  private def extractParallelism(processDeploymentData: ProcessDeploymentData): Option[Int] = processDeploymentData match {
    case GraphProcess(processAsJson) =>
      val canonicalProcess = ProcessMarshaller.fromJson(processAsJson).valueOr(err => throw new IllegalArgumentException(err.msg))
      canonicalProcess.metaData.parallelism
    case _ =>
      None
  }

  private def extractProcessConfig: String = {
    val configName = flinkConf.getString("processConfig")
    config.getConfig(configName).root().render()
  }

  override def findJobStatus(name: String): Future[Option[ProcessState]] = {
    listJobs().map(_.runningJobs.toList.filter(_.getJobName == name).map(st => ProcessState(st.getJobId.toString,
      st.getJobState.toString, st.getStartTime)).headOption)
  }

  override def cancel(name: String): Future[Unit] = {
    listJobs().flatMap(jobs => {
      val maybeJob = jobs.runningJobs.toList.find(_.getJobName == name)
      val id = maybeJob.getOrElse(throw new IllegalStateException(s"Job $name not found")).getJobId
      cancelJobById(id)
    })
  }

  private def cancelJobById(jobID: JobID)
  = gateway.invokeJobManager[CancellationSuccess](CancelJob(jobID)).map(_ => ())

  private def listJobs(): Future[RunningJobsStatus] = {
    gateway.invokeJobManager[RunningJobsStatus](JobManagerMessages.getRequestRunningJobsStatus)
  }


}

