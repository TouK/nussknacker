package pl.touk.esp.engine.management

import java.io.File
import java.util.concurrent.{TimeUnit, TimeoutException}

import akka.pattern.AskTimeoutException
import com.typesafe.config.{Config, ConfigValueType}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.JobID
import org.apache.flink.client.program.{ProgramInvocationException, PackagedProgram, StandaloneClusterClient}
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.akka.AkkaUtils
import org.apache.flink.runtime.client.{JobTimeoutException, JobClient}
import org.apache.flink.runtime.instance.ActorGateway
import org.apache.flink.runtime.messages.JobManagerMessages
import org.apache.flink.runtime.messages.JobManagerMessages._
import org.apache.flink.runtime.util.LeaderRetrievalUtils
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

class DefaultFlinkGateway(config: Configuration, timeout: FiniteDuration) extends FlinkGateway {

  implicit val ec = ExecutionContext.Implicits.global

  var actorSystem = JobClient.startJobClientActorSystem(config)

  var gateway: ActorGateway = prepareGateway(config)

  var client: StandaloneClusterClient = createClient()

  override def invokeJobManager[Response: ClassTag](req: AnyRef): Future[Response] = {
    gateway.ask(req, timeout)
      .mapTo[Response]
  }

  override def run(program: PackagedProgram, maybeParalellism: Option[Int]): Unit = {
    client.run(program, maybeParalellism.getOrElse(-1))
  }

  def shutDown(): Unit = {
    actorSystem.shutdown()
    actorSystem.awaitTermination(timeout)
    client.shutdown()
  }

  private def createClient() =
    new StandaloneClusterClient(config) {
      setDetached(true)
    }

  private def prepareGateway(config: Configuration): ActorGateway = {
    val timeout = AkkaUtils.getClientTimeout(config)
    val leaderRetrievalService = LeaderRetrievalUtils.createLeaderRetrievalService(config)
    LeaderRetrievalUtils.retrieveLeaderGateway(leaderRetrievalService, actorSystem, timeout)
  }

}

//TODO: tak w sumie to przydaloby sie to zrobic na aktorach w ui moze??
class RestartableFlinkGateway(prepareGateway: () => FlinkGateway) extends FlinkGateway with LazyLogging {

  implicit val ec = ExecutionContext.Implicits.global

  @volatile var gateway: FlinkGateway = null

  override def invokeJobManager[Response: ClassTag](req: AnyRef): Future[Response] = {
    tryToInvokeJobManager[Response](req)
      .recoverWith {
        case e: AskTimeoutException =>
          logger.error("Failed to connect to Flink, restarting", e)
          restart()
          tryToInvokeJobManager[Response](req)
      }
  }

  private def tryToInvokeJobManager[Response: ClassTag](req: AnyRef): Future[Response] = {
    retrieveGateway().invokeJobManager[Response](req)
  }

  override def run(program: PackagedProgram, maybeParalellism: Option[Int]): Unit = {
    tryToRunProgram(program, maybeParalellism).recover {
      //TODO: jaki powinien byc ten wyjatek??
      case e: TimeoutException =>
        logger.error("Failed to connect to Flink, restarting", e)
        restart()
        tryToRunProgram(program, maybeParalellism)
    }.get
  }

  private def tryToRunProgram(program: PackagedProgram, maybeParalellism: Option[Int]): Try[Unit] =
    Try(retrieveGateway().run(program, maybeParalellism))

  private def restart(): Unit = synchronized {
    shutDown()
    retrieveGateway()
  }

  def retrieveGateway() = synchronized {
    if (gateway == null) {
      logger.info("Creating new gateway")
      gateway = prepareGateway()
      logger.info("Gateway created")
    }
    gateway
  }

  override def shutDown() = synchronized {
    Option(gateway).foreach(_.shutDown())
    gateway = null
    logger.info("Gateway shut down")
  }
}

trait FlinkGateway {
  def invokeJobManager[Response: ClassTag](req: AnyRef): Future[Response]

  def run(program: PackagedProgram, maybeParalellism: Option[Int]): Unit

  def shutDown(): Unit
}

class FlinkProcessManager(config: Config,
                          gateway: FlinkGateway) extends ProcessManager with LazyLogging {

  //tyle nam wystarczy, z aktorami to nigdy nic nie wiadomo...
  implicit val ec = ExecutionContext.Implicits.global

  private val flinkConf = config.getConfig("flinkConfig")

  override def deploy(processId: String, processAsJson: String) = {
    val processClass = "pl.touk.esp.engine.process.FlinkProcessMain"

    val jarFile = new File(flinkConf.getString("jarPath"))
    val configPart = extractProcessConfig

    val maybeParalellism = extractParallelism(processAsJson)
    val program = new PackagedProgram(jarFile, processClass, List(processAsJson, configPart): _*)

    import cats.data.OptionT
    import cats.implicits._

    val stoppingResult = for {
      maybeOldJob <- OptionT(findJobStatus(processId))
      maybeSavePoint <- OptionT.liftF(stopSavingSavepoint(maybeOldJob))
    } yield maybeSavePoint

    stoppingResult.value.map { maybeSavepoint =>
      maybeSavepoint.foreach(program.setSavepointPath)
      Try(gateway.run(program, maybeParalellism)).recover {
        //TODO: jest blad we flink, future nie dostaje odpowiedzi jak poleci wyjatek przy savepoincie :|
        case e:ProgramInvocationException if e.getCause.isInstanceOf[JobTimeoutException] && maybeSavepoint.isDefined =>
          program.setSavepointPath(null)
          logger.info(s"Failed to run $processId with savepoint, trying with empty state")
          gateway.run(program, maybeParalellism)
      }.get
    }
  }

  private def stopSavingSavepoint(job: JobState): Future[String] = {
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

  private def extractParallelism(processAsJson: String): Option[Int] = {
    val canonicalProcess = ProcessMarshaller.fromJson(processAsJson).valueOr(err => throw new IllegalArgumentException(err.msg))
    canonicalProcess.metaData.parallelism
  }

  private def extractProcessConfig: String = {
    val configName = flinkConf.getString("processConfig")
    config.getConfig(configName).root().render()
  }

  override def findJobStatus(name: String): Future[Option[JobState]] = {
    listJobs().map(_.runningJobs.toList.filter(_.getJobName == name).map(st => JobState(st.getJobId.toString,
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
