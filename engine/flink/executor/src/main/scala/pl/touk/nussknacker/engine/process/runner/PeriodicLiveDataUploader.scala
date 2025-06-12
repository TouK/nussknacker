package pl.touk.nussknacker.engine.process.runner

import io.circe.syntax.EncoderOps
import org.apache.flink.api.common.functions.OpenContext
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.util.Collector
import org.slf4j.LoggerFactory
import pl.touk.nussknacker.engine.ModelConfig.LiveDataPreviewMode.DbUploader
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.livedata.LiveDataCollectingListenerHolder

import java.sql.{Connection, DriverManager}
import java.time.Instant
import scala.util.{Failure, Success, Try}

object PeriodicLiveDataUploader {

  def register(
      env: StreamExecutionEnvironment,
      processIdWithName: ProcessIdWithName,
      dbUploader: DbUploader,
  ): Unit = {
    env
      .addSource(new OneShotSource)
      .keyBy((_: String) => "live-data")
      .process(
        new PeriodicQueryableStateUpdater(
          processIdWithName = processIdWithName,
          intervalSeconds = dbUploader.uploadIntervalInSeconds,
          dbUrl = dbUploader.dbUrl,
          dbUser = dbUploader.dbUser,
          dbPassword = dbUploader.dbPassword,
          dbSchema = dbUploader.dbSchema,
        )
      )
      .addSink(new EmptySink[String])
  }

  private class OneShotSource extends SourceFunction[String] {

    override def run(ctx: SourceFunction.SourceContext[String]): Unit = {
      // emit once and sleep forever
      ctx.collect("init")
      Thread.sleep(Long.MaxValue)
    }

    override def cancel(): Unit = ()

  }

  class EmptySink[T] extends SinkFunction[T] {
    override def invoke(value: T, context: SinkFunction.Context): Unit = ()
  }

  private class PeriodicQueryableStateUpdater(
      processIdWithName: ProcessIdWithName,
      intervalSeconds: Int,
      dbUrl: String,
      dbUser: String,
      dbPassword: String,
      dbSchema: String,
  ) extends KeyedProcessFunction[String, String, String] {

    private val logger = LoggerFactory.getLogger(getClass)

    @transient private var connection: Connection = _

    override def open(openContext: OpenContext): Unit = {
      Class.forName("org.postgresql.Driver")
    }

    override def processElement(
        value: String,
        ctx: KeyedProcessFunction[String, String, String]#Context,
        out: Collector[String]
    ): Unit = {
      val ts = ctx.timerService.currentProcessingTime + 5000
      ctx.timerService.registerProcessingTimeTimer(ts)
    }

    override def onTimer(
        timestamp: Long,
        ctx: KeyedProcessFunction[String, String, String]#OnTimerContext,
        out: Collector[String]
    ): Unit = {
      val liveDataOpt = LiveDataCollectingListenerHolder.getLiveDataPreview(processIdWithName.name)
      Try {
        if (connection == null) connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword)
        connection.setSchema(dbSchema)
        val insertStatement = connection.prepareStatement(
          """
            |INSERT INTO live_data (scenario_id, collector_id, live_data, updated_at)
            |VALUES (?, ?, ?, ?)
            |ON CONFLICT (scenario_id, collector_id) DO UPDATE
            |SET live_data = EXCLUDED.live_data, updated_at = EXCLUDED.updated_at
            |""".stripMargin
        )
        insertStatement.setLong(1, processIdWithName.id.value)
        insertStatement.setString(2, LiveDataCollectingListenerHolder.id.toString)
        liveDataOpt match {
          case Some(liveData) =>
            insertStatement.setString(3, liveData.asJson.noSpaces)
          case None =>
            insertStatement.setNull(3, java.sql.Types.VARCHAR)
        }
        insertStatement.setLong(4, Instant.now.getEpochSecond)
        insertStatement.executeUpdate()
      } match {
        case Success(_) =>
          logger.debug("Uploaded scenario live data")
        case Failure(exception) =>
          connection = null
          logger.error("Could not update scenario live data", exception)
          Option(exception.getCause)
            .foreach(cause => logger.error("Could not update scenario live data with cause", cause))
      }

      ctx.timerService.registerProcessingTimeTimer(
        timestamp + intervalSeconds * 1000
      )
    }

    override def close(): Unit = {
      if (connection != null) connection.close()
    }

  }

}
