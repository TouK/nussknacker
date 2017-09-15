package pl.touk.nussknacker.engine.management

import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.TimeUnit

import argonaut.PrettyParams
import com.typesafe.config.{Config, ConfigObject, ConfigValueFactory, ConfigValueType}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.JobID
import org.apache.flink.client.program.{PackagedProgram, ProgramInvocationException}
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.client.JobTimeoutException
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import org.apache.flink.runtime.messages.JobManagerMessages
import org.apache.flink.runtime.messages.JobManagerMessages._
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.test.TestData
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ObjectProcessDefinition
import pl.touk.nussknacker.engine.definition._
import pl.touk.nussknacker.engine.flink.queryablestate.{EspQueryableClient, QueryableClientProvider}
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.loader.JarClassLoader

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object FlinkProcessManager {

  def apply(config: Config): FlinkProcessManager = {
    new FlinkProcessManager(config, new RestartableFlinkGateway(() => prepareGateway(config)))
  }

  def prepareGateway(config: Config) : FlinkGateway = {
    val flinkConf = prepareFlinkConfig(config)

    val timeout = flinkConf.getDuration("jobManagerTimeout", TimeUnit.MILLISECONDS) millis

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
    new DefaultFlinkGateway(clientConfig, timeout)
  }

  private def prepareFlinkConfig(config: Config) : Config = config.getConfig("flinkConfig")
    //TODO: flink requires this value, although it's not used by client...
    .withValue("high-availability.storageDir", ConfigValueFactory.fromAnyRef("file:///dev/null"))

}


class FlinkProcessManager(config: Config,
                          gateway: FlinkGateway) extends ProcessManager
                            with ConfigCreatorTestInfoProvider
                            with ProcessDefinitionProvider with ConfigCreatorSignalDispatcher with QueryableClientProvider with LazyLogging {

  import argonaut.Argonaut._

  private val flinkConf = config.getConfig("flinkConfig")

  private val processConfigPart = extractProcessConfig

  override val processConfig : Config = processConfigPart.toConfig

  private implicit val ec = ExecutionContext.Implicits.global

  private val jarClassLoader = JarClassLoader(flinkConf.getString("jarPath"))

  private lazy val testRunner = FlinkProcessTestRunner(processConfig, jarClassLoader.file)

  private lazy val verification = FlinkProcessVerifier(processConfig, jarClassLoader.file)

  override lazy val configCreator: ProcessConfigCreator =
    jarClassLoader.createProcessConfigCreator

  override def deploy(processId: String, processDeploymentData: ProcessDeploymentData, savepointPath: Option[String]) = {
    val program = prepareProgram(processId, processDeploymentData)

    import cats.data.OptionT
    import cats.implicits._

    val stoppingResult = for {
      maybeOldJob <- OptionT(findJobStatus(processId))
      maybeSavePoint <- {
        { logger.debug(s"Deploying $processId. Status: $maybeOldJob") }
        OptionT.liftF(stopSavingSavepoint(processId, maybeOldJob, processDeploymentData))
      }
    } yield {
      logger.info(s"Deploying $processId. Saving savepoint finished")
      maybeSavePoint
    }

    stoppingResult.value.map { maybeSavepoint =>
      //savepoint given by user overrides the one created by flink
      prepareSavepointSettings(processId, program, savepointPath.orElse(maybeSavepoint))
      logger.info(s"Using savepoint ${savepointPath.orElse(maybeSavepoint)}")
      gateway.run(program)
    }
  }


  override def savepoint(processId: String, savepointDir: String): Future[String] = {
    findJobStatus(processId).flatMap {
      case Some(processState) => makeSavepoint(processState, Some(savepointDir))
      case None => Future.failed(new Exception("Process not found"))
    }
  }

  override def queryableClient : EspQueryableClient = gateway.queryableClient

  override def test(processId: String, json: String, testData: TestData) = {
    Future(testRunner.test(processId, json, testData))
  }

  override def getProcessDefinition = {
    ThreadUtils.withThisAsContextClassLoader(jarClassLoader.classLoader) {
      ObjectProcessDefinition(ProcessDefinitionExtractor.extractObjectWithMethods(configCreator, processConfig))
    }
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

  private def prepareSavepointSettings(processId: String, program: PackagedProgram, maybeSavepoint: Option[String]): Unit = {
    maybeSavepoint.foreach(path => program.setSavepointRestoreSettings(SavepointRestoreSettings.forPath(path, true)))
    logger.info(s"Deploying $processId. Setting savepoint (${program.getSavepointSettings}) finished")
  }

  private lazy val buildInfoJson = {
    configCreator.buildInfo().asJson.pretty(PrettyParams.spaces2.copy(preserveOrder = true))
  }

  private def checkIfJobIsCompatible(processId: String, savepointPath: String, processDeploymentData: ProcessDeploymentData) : Future[Unit] = processDeploymentData match {
    case GraphProcess(processAsJson) if shouldVerifyBeforeDeploy =>
      verification.verify(processId, processAsJson, savepointPath)
    case _ => Future.successful(())
  }

  private def shouldVerifyBeforeDeploy :Boolean = {
    val verifyConfigProperty = "verifyBeforeDeploy"
    !config.hasPath(verifyConfigProperty) || config.getBoolean(verifyConfigProperty)
  }

  private def stopSavingSavepoint(processId: String, job: ProcessState, processDeploymentData: ProcessDeploymentData): Future[String] = {
    for {
      savepointPath <- makeSavepoint(job, None)
      _ <- checkIfJobIsCompatible(processId, savepointPath, processDeploymentData)
      _ <- cancel(job)
    } yield savepointPath
  }

  private def cancel(job: ProcessState) = {
    val jobId = JobID.fromHexString(job.id)
    gateway.invokeJobManager[CancellationSuccess](CancelJob(jobId)).map(_ => ())
  }

  private def makeSavepoint(job: ProcessState, savepointDir: Option[String]): Future[String] = {
    val jobId = JobID.fromHexString(job.id)

    gateway.invokeJobManager[Any](JobManagerMessages.TriggerSavepoint(jobId, savepointDir)).map {
      case TriggerSavepointSuccess(_, checkpointId, path, triggerTime) =>
        logger.info(s"Got savepoint: ${path}")
        path
      case TriggerSavepointFailure(_, reason) =>
        logger.error(s"Savepoint failed for $jobId(${job.status}) - $savepointDir", reason)
        throw reason
    }
  }

  private def extractProcessConfig: ConfigObject = {
    config.getConfig("processConfig").root()
  }

  private def cancelJobById(jobID: JobID)
  = gateway.invokeJobManager[CancellationSuccess](CancelJob(jobID)).map(_ => ())

  private def listJobs(): Future[RunningJobsStatus] = {
    gateway.invokeJobManager[RunningJobsStatus](JobManagerMessages.getRequestRunningJobsStatus)
  }

  private def prepareProgram(processId: String, processDeploymentData: ProcessDeploymentData) : PackagedProgram = {
    val configPart = processConfigPart.render()
    processDeploymentData match {
      case GraphProcess(processAsJson) =>
        new PackagedProgram(jarClassLoader.file, "pl.touk.nussknacker.engine.process.runner.FlinkProcessMain", List(processAsJson, configPart, buildInfoJson):_*)
      case CustomProcess(mainClass) =>
        new PackagedProgram(jarClassLoader.file, mainClass, List(processId, configPart, buildInfoJson): _*)
    }
  }
}

