package pl.touk.esp.engine.management

import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.TimeUnit

import argonaut.PrettyParams
import com.typesafe.config.{Config, ConfigObject, ConfigValueType}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.JobID
import org.apache.flink.client.program.{PackagedProgram, ProgramInvocationException}
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.client.JobTimeoutException
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import org.apache.flink.runtime.messages.JobManagerMessages
import org.apache.flink.runtime.messages.JobManagerMessages._
import pl.touk.esp.engine.api.deployment._
import pl.touk.esp.engine.api.deployment.test.TestData
import pl.touk.esp.engine.api.process.ProcessConfigCreator
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ObjectProcessDefinition
import pl.touk.esp.engine.definition._
import pl.touk.esp.engine.flink.queryablestate.{EspQueryableClient, QueryableClientProvider}
import pl.touk.esp.engine.util.ThreadUtils
import pl.touk.esp.engine.util.loader.JarClassLoader

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object FlinkProcessManager {

  def apply(config: Config): FlinkProcessManager = {
    new FlinkProcessManager(config, new RestartableFlinkGateway(() => prepareGateway(config)))
  }

  def prepareGateway(config: Config) : FlinkGateway = {
    val flinkConf = config.getConfig("flinkConfig")

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

}


class FlinkProcessManager(config: Config,
                          gateway: FlinkGateway) extends ProcessManager
                            with ConfigCreatorTestInfoProvider
                            with ProcessDefinitionProvider with ConfigCreatorSignalDispatcher with QueryableClientProvider with LazyLogging {

  import argonaut.Argonaut._

  //tyle nam wystarczy, z aktorami to nigdy nic nie wiadomo...
  private implicit val ec = ExecutionContext.Implicits.global

  private val flinkConf = config.getConfig("flinkConfig")

  override def queryableClient : EspQueryableClient = gateway.queryableClient
  private val jarClassLoader = JarClassLoader(flinkConf.getString("jarPath"))

  private val processConfigPart = extractProcessConfig

  val processConfig : Config = processConfigPart.toConfig

  private val testRunner = FlinkProcessTestRunner(processConfig, jarClassLoader.file)

  lazy val buildInfo: Map[String, String] = configCreator.buildInfo()

  lazy val configCreator: ProcessConfigCreator =
    jarClassLoader.createProcessConfigCreator(processConfig.getString("processConfigCreatorClass"))

  private def prepareProgram(processId: String, processDeploymentData: ProcessDeploymentData) : PackagedProgram = {
    val configPart = processConfigPart.render()
    processDeploymentData match {
      case GraphProcess(processAsJson) =>
        new PackagedProgram(jarClassLoader.file, "pl.touk.esp.engine.process.runner.FlinkProcessMain", List(processAsJson, configPart, buildInfoJson):_*)
      case CustomProcess(mainClass) =>
        new PackagedProgram(jarClassLoader.file, mainClass, List(processId, configPart, buildInfoJson): _*)
    }
  }


  override def deploy(processId: String, processDeploymentData: ProcessDeploymentData, savepointPath: Option[String]) = {
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
      logger.info(s"Deploying $processId. Saving savepoint finished")
      maybeSavePoint
    }

    stoppingResult.value.map { maybeSavepoint =>
      //savepoint given by user overrides the one created by flink
      prepareSavepointSettings(processId, program, savepointPath.orElse(maybeSavepoint))
      Try(gateway.run(program)).recover {
        //TODO: jest blad we flink, future nie dostaje odpowiedzi jak poleci wyjatek przy savepoincie :|
        case e:ProgramInvocationException if e.getCause.isInstanceOf[JobTimeoutException] && maybeSavepoint.isDefined =>
          program.setSavepointRestoreSettings(SavepointRestoreSettings.none())
          logger.warn(s"Failed to run $processId with savepoint, trying with empty state")
          gateway.run(program)
      }.get
    }
  }


  override def savepoint(processId: String, savepointDir: String): Future[String] = {
    findJobStatus(processId).flatMap {
      case Some(processState) => makeSavepoint(processState, Some(savepointDir))
      case None => Future.failed(new Exception("Process not found"))
    }
  }

  private def prepareSavepointSettings(processId: String, program: PackagedProgram, maybeSavepoint: Option[String]): Unit = {
    maybeSavepoint.foreach(path => program.setSavepointRestoreSettings(SavepointRestoreSettings.forPath(path, true)))
    logger.info(s"Deploying $processId. Setting savepoint (${program.getSavepointSettings}) finished")
  }

  override def test(processId: String, json: String, testData: TestData) = {
    Future(testRunner.test(processId, json, testData))
  }

  private lazy val buildInfoJson = {
    buildInfo.asJson.pretty(PrettyParams.spaces2.copy(preserveOrder = true))
  }

  override def getProcessDefinition = {
    ThreadUtils.withThisAsContextClassLoader(jarClassLoader.classLoader) {
      ObjectProcessDefinition(ProcessDefinitionExtractor.extractObjectWithMethods(configCreator, processConfig))
    }
  }

  private def stopSavingSavepoint(job: ProcessState): Future[String] = {
    for {
      savepointPath <- makeSavepoint(job, None)
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
        path
      case TriggerSavepointFailure(_, reason) =>
        logger.error("Savepoint failed", reason)
        throw reason
    }
  }

  private def extractProcessConfig: ConfigObject = {
    val configName = flinkConf.getString("processConfig")
    config.getConfig(configName).root()
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

