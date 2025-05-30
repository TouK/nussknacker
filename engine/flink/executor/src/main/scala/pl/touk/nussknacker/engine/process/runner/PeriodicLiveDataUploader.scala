package pl.touk.nussknacker.engine.process.runner

import org.apache.flink.api.common.functions.OpenContext
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.util.Collector
import org.slf4j.LoggerFactory
import pl.touk.nussknacker.engine.ModelConfig.LiveDataPreviewMode.DbUploader
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName

import java.sql.{Connection, DriverManager, PreparedStatement}
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

  class OneShotSource extends SourceFunction[String] {

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

  class PeriodicQueryableStateUpdater(
      processIdWithName: ProcessIdWithName,
      intervalSeconds: Int,
      dbUrl: String,
      dbUser: String,
      dbPassword: String,
      dbSchema: String,
  ) extends KeyedProcessFunction[String, String, String] {

    private val logger = LoggerFactory.getLogger(getClass)

    @transient private var connection: Connection             = _
    @transient private var insertStatement: PreparedStatement = _

    override def open(openContext: OpenContext): Unit = {
      Class.forName("org.postgresql.Driver")
      connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword)
      insertStatement = connection.prepareStatement(
        s"""
          |INSERT INTO $dbSchema.flink_live_data (scenario_id, live_data, updated_at)
          |VALUES (?, ?, ?)
          |ON CONFLICT (scenario_id) DO UPDATE
          |SET live_data = EXCLUDED.live_data, updated_at = EXCLUDED.updated_at
          |""".stripMargin
      )
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
      Try {
        insertStatement.setLong(1, processIdWithName.id.value)
        insertStatement.setNull(2, java.sql.Types.VARCHAR)
        insertStatement.setLong(3, Instant.now.toEpochMilli)
        insertStatement.executeUpdate()
      } match {
        case Success(_) =>
          logger.info("Uploaded scenario live data")
        case Failure(exception) =>
          logger.error("Could not update scenario live data", exception)
      }

      ctx.timerService.registerProcessingTimeTimer(
        timestamp + intervalSeconds * 1000
      )
    }

    override def close(): Unit = {
      if (insertStatement != null) insertStatement.close()
      if (connection != null) connection.close()
    }

  }

}
