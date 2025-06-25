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
import pl.touk.nussknacker.engine.livedata.LiveDataCollectingListenerHolder

import java.sql.{Connection, DriverManager}
import java.time.Instant
import scala.util.{Failure, Try}

object PeriodicLiveDataUploader {

  def register(
      env: StreamExecutionEnvironment,
      processIdWithName: ProcessIdWithName,
      storage: DesignerDb,
  ): Unit = {
    env
      .fromSource(new EmitOnceSource, WatermarkStrategy.noWatermarks(), "live-data-uploader")
      .keyBy((_: String) => "live-data")
      .process(
        new PeriodicLiveDataUploader(
          processIdWithName = processIdWithName,
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
    intervalSeconds: Int,
    dbUrl: String,
    dbUser: String,
    dbPassword: String,
    dbSchema: String,
) extends KeyedProcessFunction[String, String, String] {

  private val logger = LoggerFactory.getLogger(getClass)

  @transient private var connection: Connection = _
  @transient private var running: Boolean       = _
  @transient private var updaterThread: Thread  = _

  override def open(openContext: OpenContext): Unit = {
    // Looks like unused code, but needed to load the driver
    if (dbUrl.startsWith("jdbc:postgresql:"))
      Class.forName("org.postgresql.Driver")
    else if (dbUrl.startsWith("jdbc:hsqldb:"))
      Class.forName("org.hsqldb.jdbc.JDBCDriver")

    running = true
    updaterThread = new Thread(() => {
      while (running) {
        Try {
          if (connection == null) connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword)

          try {
            if (dbUrl.startsWith("jdbc:postgresql:")) {
              connection.setSchema(dbSchema)
            }
          } catch {
            case e: java.sql.SQLFeatureNotSupportedException =>
              logger.warn("Setting schema not supported by this DB", e)
          }

          val insertStatement = connection.prepareStatement(
            if (dbUrl.startsWith("jdbc:postgresql:")) {
              """
                  |INSERT INTO live_data (scenario_id, collector_id, live_data, updated_at)
                  |VALUES (?, ?, ?, ?)
                  |ON CONFLICT (scenario_id, collector_id) DO UPDATE
                  |SET live_data = EXCLUDED.live_data, updated_at = EXCLUDED.updated_at
                  |""".stripMargin
            } else {
              """
                  |MERGE INTO "public"."live_data" AS target
                  |USING (VALUES (?, ?, ?, ?)) AS vals("scenario_id", "collector_id", "live_data", "updated_at")
                  |ON (target."scenario_id" = vals."scenario_id" AND target."collector_id" = vals."collector_id")
                  |WHEN MATCHED THEN
                  |  UPDATE SET "live_data" = vals."live_data", "updated_at" = vals."updated_at"
                  |WHEN NOT MATCHED THEN
                  |  INSERT ("scenario_id", "collector_id", "live_data", "updated_at")
                  |  VALUES (vals."scenario_id", vals."collector_id", vals."live_data", vals."updated_at")
                  |""".stripMargin
            }
          )

          insertStatement.setLong(1, processIdWithName.id.value)
          insertStatement.setString(2, LiveDataCollectingListenerHolder.id.toString)

          LiveDataCollectingListenerHolder.getLiveDataPreview(processIdWithName.name) match {
            case Some(liveData) =>
              insertStatement.setString(3, liveData.asJson.noSpaces)
            case None =>
              insertStatement.setNull(3, java.sql.Types.VARCHAR)
          }

          insertStatement.setLong(4, Instant.now.getEpochSecond)
          insertStatement.executeUpdate()

          logger.debug("Uploaded scenario live data")
        } match {
          case Failure(exception) =>
            logger.error("Could not update scenario live data", exception)
            Option(exception.getCause).foreach(cause => logger.error("Cause of the failure", cause))
            connection = null
          case _ => // already logged
        }

        try {
          Thread.sleep(intervalSeconds * 1000L)
        } catch {
          case _: InterruptedException =>
            logger.warn("Update thread interrupted, stopping...")
            running = false
        }
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
  }

}
