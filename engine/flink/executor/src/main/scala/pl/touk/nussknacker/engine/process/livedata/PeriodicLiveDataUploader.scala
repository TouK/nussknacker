package pl.touk.nussknacker.engine.process.livedata

import io.circe.syntax.EncoderOps
import org.slf4j.LoggerFactory
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.livedata.LiveDataCollectingListenerHolder

import java.sql.{Connection, DriverManager, PreparedStatement}
import java.time.Instant
import scala.util.{Failure, Try}

class PeriodicLiveDataUploader(
    processIdWithName: ProcessIdWithName,
    intervalSeconds: Int,
    dbUrl: String,
    dbUser: String,
    dbPassword: String,
    dbSchema: String,
) {

  private val logger = LoggerFactory.getLogger(getClass)

  @transient private var running: Boolean      = _
  @transient private var updaterThread: Thread = _

  @transient private var connection: Connection       = _
  @transient private var statement: PreparedStatement = _

  def start(): Unit = {
    running = true
    loadJdbcDriver()
    updaterThread = new Thread(() => {
      while (running) {
        Try {
          if (connection == null) {
            prepareConnection()
            prepareStatement()
          }
          uploadLiveData()
          logger.debug("Uploaded scenario live data")
        } match {
          case Failure(exception) =>
            // If uploading fails, we skip this entry, close connection and try again after the scheduled interval
            logger.error(
              "Could not update scenario live data. The scenario is running, but it was impossible to upload the current live data to the db. It may be caused by misconfiguration. Please check the detailed reason of failure in logs below.",
              exception
            )
            Option(exception.getCause).foreach(cause =>
              logger.error("Detailed cause of the scenario live data uploading failure:", cause)
            )
            connection.close()
            connection = null
          case _ => ()
        }
        // Sleep until next scheduled upload, or close
        try {
          Thread.sleep(intervalSeconds * 1000)
        } catch {
          case _: InterruptedException =>
            logger.warn("Update thread interrupted, stopping...")
            running = false
        }
      }
    })
    updaterThread.start()
  }

  def close(): Unit = {
    running = false
    if (updaterThread != null) {
      updaterThread.interrupt()
      updaterThread.join()
    }
    if (connection != null) connection.close()
    if (statement != null) statement.close()
  }

  private def loadJdbcDriver(): Unit = {
    // Looks like unused code, but needed to load the driver
    if (dbUrl.startsWith("jdbc:postgresql:"))
      Class.forName("org.postgresql.Driver")
    else if (dbUrl.startsWith("jdbc:hsqldb:"))
      Class.forName("org.hsqldb.jdbc.JDBCDriver")
  }

  private def prepareConnection(): Unit = {
    connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword)
    connection.setSchema(dbSchema)
  }

  private def prepareStatement(): Unit = {
    statement = if (dbUrl.startsWith("jdbc:postgresql:")) {
      connection.prepareStatement(
        """
          |INSERT INTO live_data (scenario_id, collector_id, live_data, updated_at)
          |VALUES (?, ?, ?, ?)
          |ON CONFLICT (scenario_id, collector_id) DO UPDATE
          |SET live_data = EXCLUDED.live_data, updated_at = EXCLUDED.updated_at
          |""".stripMargin
      )
    } else {
      connection.prepareStatement(
        s"""
           |MERGE INTO "$dbSchema"."live_data" AS target
           |USING (VALUES (?, ?, ?, ?)) AS vals("scenario_id", "collector_id", "live_data", "updated_at")
           |ON (target."scenario_id" = vals."scenario_id" AND target."collector_id" = vals."collector_id")
           |WHEN MATCHED THEN
           |  UPDATE SET "live_data" = vals."live_data", "updated_at" = vals."updated_at"
           |WHEN NOT MATCHED THEN
           |  INSERT ("scenario_id", "collector_id", "live_data", "updated_at")
           |  VALUES (vals."scenario_id", vals."collector_id", vals."live_data", vals."updated_at")
           |""".stripMargin
      )
    }
  }

  private def uploadLiveData(): Unit = {
    statement.setLong(1, processIdWithName.id.value)
    statement.setString(2, LiveDataCollectingListenerHolder.id.toString)
    LiveDataCollectingListenerHolder.getLiveDataPreview(processIdWithName.name) match {
      case Some(liveData) =>
        statement.setString(3, liveData.asJson.noSpaces)
      case None =>
        statement.setNull(3, java.sql.Types.VARCHAR)
    }
    statement.setLong(4, Instant.now.getEpochSecond)
    statement.executeUpdate()
  }

}
