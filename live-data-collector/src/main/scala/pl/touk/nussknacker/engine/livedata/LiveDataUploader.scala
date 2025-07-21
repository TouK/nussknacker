package pl.touk.nussknacker.engine.livedata

import io.circe.syntax.EncoderOps
import org.slf4j.LoggerFactory
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.livedata.LiveDataUploader.LiveDataUploaderConfig
import pl.touk.nussknacker.engine.newdeployment.DeploymentId

import java.sql.{Connection, DriverManager, PreparedStatement}
import java.time.Instant
import java.util.UUID
import scala.util.Try

private[livedata] class LiveDataUploader(config: LiveDataUploaderConfig) {

  private val logger = LoggerFactory.getLogger(getClass)

  @transient private var connection: Connection       = _
  @transient private var statement: PreparedStatement = _

  def start(): Unit = {
    loadJdbcDriver()
    prepareConnection()
    prepareStatement()
  }

  def close(): Unit = {
    if (statement != null) statement.close()
    if (connection != null) connection.close()
  }

  def uploadLiveData(
      processIdWithName: ProcessIdWithName,
      deploymentId: DeploymentId,
      collectedLiveData: CollectedLiveData,
  ): Unit = Try {
    if (connection == null) {
      prepareConnection()
      prepareStatement()
    }
    doUploadLiveData(processIdWithName, deploymentId, collectedLiveData)
  }.recover { case ex => handleLiveDataUploadFailure(ex) }

  private def handleLiveDataUploadFailure(ex: Throwable): Unit = {
    // If uploading fails, we skip this entry, close connection and try again after the scheduled interval
    logger.error(
      s"Could not upload scenario live data to the db. This upload is skipped and will be retried on next scheduled upload time in ${config.intervalSeconds}s. If uploading continues to fail, then please check the detailed reason of failure in logs below and look for configuration issues.",
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

  private def loadJdbcDriver(): Unit = {
    // Looks like unused code, but needed to load the driver
    if (config.dbUrl.startsWith("jdbc:postgresql:"))
      Class.forName("org.postgresql.Driver")
    else if (config.dbUrl.startsWith("jdbc:hsqldb:"))
      Class.forName("org.hsqldb.jdbc.JDBCDriver")
  }

  private def prepareConnection(): Unit = {
    if (connection != null) connection.close()
    connection = DriverManager.getConnection(config.dbUrl, config.dbUser, config.dbPassword)
  }

  private def prepareStatement(): Unit = {
    if (statement != null) statement.close()
    statement = if (config.dbUrl.startsWith("jdbc:postgresql:")) {
      connection.prepareStatement(
        s"""
          |INSERT INTO "${config.dbSchema}"."live_data" (scenario_id, deployment_id, collector_id, live_data, updated_at)
          |VALUES (?, ?, ?, ?, ?)
          |ON CONFLICT (scenario_id, deployment_id, collector_id) DO UPDATE
          |SET live_data = EXCLUDED.live_data, updated_at = EXCLUDED.updated_at
          |""".stripMargin
      )
    } else {
      connection.prepareStatement(
        s"""
           |MERGE INTO "${config.dbSchema}"."live_data" AS target
           |USING (VALUES (?, ?, ?, ?, ?)) AS vals("scenario_id", "deployment_id", "collector_id", "live_data", "updated_at")
           |ON (target."scenario_id" = vals."scenario_id" AND target."deployment_id" = vals."deployment_id" AND target."collector_id" = vals."collector_id")
           |WHEN MATCHED THEN
           |  UPDATE SET "live_data" = vals."live_data", "updated_at" = vals."updated_at"
           |WHEN NOT MATCHED THEN
           |  INSERT ("scenario_id", "deployment_id", "collector_id", "live_data", "updated_at")
           |  VALUES (vals."scenario_id", vals."deployment_id", vals."collector_id", vals."live_data", vals."updated_at")
           |""".stripMargin
      )
    }
  }

  private def doUploadLiveData(
      processIdWithName: ProcessIdWithName,
      deploymentId: DeploymentId,
      collectedLiveData: CollectedLiveData
  ): Unit = {
    statement.setLong(1, processIdWithName.id.value)
    statement.setObject(2, deploymentId.value)
    statement.setString(3, LiveDataUploader.collectorId.toString)
    statement.setString(4, collectedLiveData.asJson.noSpaces)
    statement.setLong(5, Instant.now.getEpochSecond)
    statement.executeUpdate()
  }

}

object LiveDataUploader {

  private val collectorId: UUID = UUID.randomUUID()

  final case class LiveDataUploaderConfig(
      intervalSeconds: Int,
      uploaderInactivityTimeoutInSeconds: Int,
      dbUrl: String,
      dbUser: String,
      dbPassword: String,
      dbSchema: String
  )

}
