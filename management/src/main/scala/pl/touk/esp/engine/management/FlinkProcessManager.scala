package pl.touk.esp.engine.management

import java.io.File
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigValueType}
import org.apache.flink.api.common.JobID
import org.apache.flink.client.program.{Client, PackagedProgram}
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.akka.AkkaUtils
import org.apache.flink.runtime.client.JobClient
import org.apache.flink.runtime.instance.ActorGateway
import org.apache.flink.runtime.messages.JobManagerMessages
import org.apache.flink.runtime.messages.JobManagerMessages.{CancellationSuccess, CancelJob, RunningJobsStatus}
import org.apache.flink.runtime.util.LeaderRetrievalUtils

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

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
    val actorSystem = JobClient.startJobClientActorSystem(clientConfig)

    new FlinkProcessManager(flinkConf, new Client(clientConfig), actorSystem.dispatcher,
      prepareGateway(clientConfig, actorSystem))
  }

  private def prepareGateway(config: Configuration, actorSystem: ActorSystem): ActorGateway = {
    val timeout = AkkaUtils.getClientTimeout(config)
    val leaderRetrievalService = LeaderRetrievalUtils.createLeaderRetrievalService(config)
    LeaderRetrievalUtils.retrieveLeaderGateway(leaderRetrievalService, actorSystem, timeout)
  }
}

class FlinkProcessManager(flinkConf: Config, client: Client,
                          executionContext: ExecutionContext,
                          gateway: ActorGateway) extends ProcessManager {

  implicit val ec = executionContext

  override def deploy(processId: String, processAsJson: String) = {

    val processClass = "pl.touk.esp.engine.process.FlinkProcessMain"


    val jarFile = new File(flinkConf.getString("jarPath"))
    val configPart = flinkConf.getString("processConfig")

    val program = new PackagedProgram(jarFile, processClass, List(processAsJson, configPart).toArray: _*)

    findJobStatus(processId).map(maybeOldJob => {
       maybeOldJob.foreach { job =>
         client.cancel(JobID.fromHexString(job.id))
       }
    }).map(_ => client.runDetached(program, flinkConf.getInt("parallelism")))
  }


  override def findJobStatus(name: String) = {
    listJobs().map(_.runningJobs.toList.filter(_.getJobName == name).map(st => JobState(st.getJobId.toString,
      st.getJobState.toString, st.getStartTime)).headOption)
  }


  override def cancel(name: String) = {
    listJobs().flatMap(jobs => {
      val maybeJob = jobs.runningJobs.toList.find(_.getJobName == name)
      val id = maybeJob.getOrElse(throw new IllegalStateException(s"Job $name not found")).getJobId
      invokeJobManager[CancellationSuccess](CancelJob(id)).map(_ => ())
    })
  }

  private def listJobs() : Future[RunningJobsStatus] = {
    invokeJobManager[RunningJobsStatus](JobManagerMessages.getRequestRunningJobsStatus)
  }

  private def invokeJobManager[Response:ClassTag](req: AnyRef) : Future[Response] = {
    //??
    val timeout = (flinkConf.getDuration("jobManagerTimeout", TimeUnit.MILLISECONDS) millis)
    gateway.ask(req,  timeout).mapTo[Response]
  }

}
