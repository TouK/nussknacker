package pl.touk.nussknacker.engine.process.livedata

import io.circe.syntax.EncoderOps
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.functions.OpenContext
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink
import org.apache.flink.util.Collector
import org.slf4j.LoggerFactory
import pl.touk.nussknacker.engine.ModelConfig.LiveDataPreviewMode.LiveDataStorage.DesignerDb
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.livedata.LiveDataCollectingListenerHolder

import java.sql.{Connection, DriverManager, PreparedStatement}
import java.time.Instant
import scala.util.Try

object PeriodicLiveDataUploader {

  // Uploading live data to external storage (in order to make it available for the Designer) is done as a Flink pipeline.
  // Flink runs it alongside the scenario. It is started with the scenario and stopped when scenario is stopped.
  def register(
      env: StreamExecutionEnvironment,
      processIdWithName: ProcessIdWithName,
      deploymentData: DeploymentData,
      storage: DesignerDb,
  ): Unit = {
    env
      .fromSource(new EmitOnceSource, WatermarkStrategy.noWatermarks(), "live-data-uploader")
      .keyBy((_: String) => "live-data")
      .process(
        new PeriodicLiveDataUploader(
          processIdWithName = processIdWithName,
          deploymentData = deploymentData,
          intervalSeconds = storage.uploadIntervalInSeconds,
          dbUrl = storage.url,
          dbUser = storage.user,
          dbPassword = storage.password,
          dbSchema = storage.schema,
        )
      )
      .sinkTo(new DiscardingSink[String]())
  }

}

class PeriodicLiveDataUploader(
    processIdWithName: ProcessIdWithName,
    deploymentData: DeploymentData,
    intervalSeconds: Int,
    dbUrl: String,
    dbUser: String,
    dbPassword: String,
    dbSchema: String,
) extends KeyedProcessFunction[String, String, String] {

  private val logger = LoggerFactory.getLogger(getClass)

  @transient private var running: Boolean      = _
  @transient private var updaterThread: Thread = _

  @transient private var connection: Connection       = _
  @transient private var statement: PreparedStatement = _

  private lazy val jobId: String = getRuntimeContext.getJobInfo.getJobId.toHexString

  override def open(openContext: OpenContext): Unit = {
    running = true
    loadJdbcDriver()
    prepareConnection()
    prepareStatement()
    updaterThread = new Thread(() => {
      while (running) {
        Try(uploadLiveData()).recover { case ex => handleLiveDataUploadFailure(ex) }
        sleepUntilNextUploadTimeUnlessInterrupted()
      }
    })
    updaterThread.start()
  }

  override def processElement(
      value: String,
      ctx: KeyedProcessFunction[String, String, String]#Context,
      out: Collector[String]
  ): Unit = ()

  override def close(): Unit = {
    running = false
    if (updaterThread != null) {
      updaterThread.interrupt()
      updaterThread.join()
    }
    if (connection != null) connection.close()
    if (statement != null) statement.close()
  }

  private def uploadLiveData(): Unit = {
    if (connection == null) {
      prepareConnection()
      prepareStatement()
    }
    doUploadLiveData()
    logger.debug("Uploaded scenario live data")
  }

  private def handleLiveDataUploadFailure(ex: Throwable): Unit = {
    // If uploading fails, we skip this entry, close connection and try again after the scheduled interval
    logger.error(
      s"Could not upload scenario live data to the db. This upload is skipped and will be retried on next scheduled upload time in $intervalSeconds s. If uploading continues to fail, then please check the detailed reason of failure in logs below and look for configuration issues.",
      ex
    )
    Option(ex.getCause).foreach(cause =>
      logger.error("Detailed cause of the scenario live data uploading failure:", cause)
    )
    if (connection != null) connection.close()
    connection = null
    if (statement != null) statement.close()
    statement = null
  }

  private def sleepUntilNextUploadTimeUnlessInterrupted(): Unit = {
    try {
      Thread.sleep(intervalSeconds * 1000)
    } catch {
      case _: InterruptedException =>
        logger.warn("Update thread interrupted, stopping...")
        running = false
    }
  }

  private def loadJdbcDriver(): Unit = {
    // Looks like unused code, but needed to load the driver
    if (dbUrl.startsWith("jdbc:postgresql:"))
      Class.forName("org.postgresql.Driver")
    else if (dbUrl.startsWith("jdbc:hsqldb:"))
      Class.forName("org.hsqldb.jdbc.JDBCDriver")
  }

  private def prepareConnection(): Unit = {
    if (connection != null) connection.close()
    connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword)
    connection.setSchema(dbSchema)
  }

  private def prepareStatement(): Unit = {
    if (statement != null) statement.close()
    statement = if (dbUrl.startsWith("jdbc:postgresql:")) {
      connection.prepareStatement(
        """
          |INSERT INTO live_data (scenario_id, deployment_id, external_deployment_id, collector_id, live_data, updated_at)
          |VALUES (?, ?, ?, ?, ?, ?)
          |ON CONFLICT (scenario_id, deployment_id, external_deployment_id, collector_id) DO UPDATE
          |SET live_data = EXCLUDED.live_data, updated_at = EXCLUDED.updated_at
          |""".stripMargin
      )
    } else {
      connection.prepareStatement(
        s"""
           |MERGE INTO "$dbSchema"."live_data" AS target
           |USING (VALUES (?, ?, ?, ?, ?, ?)) AS vals("scenario_id", "deployment_id", "external_deployment_id", "collector_id", "live_data", "updated_at")
           |ON (target."scenario_id" = vals."scenario_id" AND target."deployment_id" = vals."deployment_id" AND target."external_deployment_id" = vals."external_deployment_id" AND target."collector_id" = vals."collector_id")
           |WHEN MATCHED THEN
           |  UPDATE SET "live_data" = vals."live_data", "updated_at" = vals."updated_at"
           |WHEN NOT MATCHED THEN
           |  INSERT ("scenario_id", "deployment_id", "external_deployment_id", "collector_id", "live_data", "updated_at")
           |  VALUES (vals."scenario_id", vals."deployment_id", vals."external_deployment_id", vals."collector_id", vals."live_data", vals."updated_at")
           |""".stripMargin
      )
    }
  }

  private def doUploadLiveData(): Unit = {
    statement.setLong(1, processIdWithName.id.value)
    statement.setString(2, deploymentData.deploymentId.value)
    statement.setString(3, jobId)
    statement.setString(4, LiveDataCollectingListenerHolder.id.toString)
    LiveDataCollectingListenerHolder.getLiveDataPreview(processIdWithName.name) match {
      case Some(liveData) =>
        statement.setString(5, liveData.asJson.noSpaces)
      case None =>
        statement.setNull(5, java.sql.Types.VARCHAR)
    }
    statement.setLong(6, Instant.now.getEpochSecond)
    statement.executeUpdate()
  }

}
