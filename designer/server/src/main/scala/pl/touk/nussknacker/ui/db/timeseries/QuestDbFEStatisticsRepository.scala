package pl.touk.nussknacker.ui.db.timeseries

import cats.effect.{IO, Resource}
import com.typesafe.scalalogging.LazyLogging
import io.questdb.TelemetryConfiguration
import io.questdb.cairo.security.AllowAllSecurityContext
import io.questdb.cairo.wal.ApplyWal2TableJob
import io.questdb.cairo.{CairoEngine, CairoException, DefaultCairoConfiguration}
import io.questdb.griffin.{SqlException, SqlExecutionContextImpl}
import io.questdb.log.LogFactory
import io.questdb.std.Os
import pl.touk.nussknacker.ui.db.timeseries.QuestDbFEStatisticsRepository.{createTableQuery, dropTablesQuery}

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Try, Using}

// TODO list:
// 1. Writing in a single thread and polish usage of the wal2table job.
// 2. Configurable directory of db (for now it's tmp directory) and limiting db file space on disk.
// 3. Is truncate ok on critical errors?
// 4. Collecting statistics should be deactivable by configuration.
// 5. API should have better types (missing domain layer for FE statistics names).
// 6. Handling errors while creating the QuestDb.
// 7. Write tests when db directory is deleted in runtime.
// 8. Changing table definition and recreate
private class QuestDbFEStatisticsRepository(private val cairoEngine: CairoEngine)
  extends FEStatisticsRepository[Future]
    with LazyLogging {
  private val ctx = new SqlExecutionContextImpl(cairoEngine, 1).`with`(AllowAllSecurityContext.INSTANCE, null)
  private val applyWal2TableJob = new ApplyWal2TableJob(cairoEngine, 1, 1)

  private lazy val statsWalTableWriter = {
    cairoEngine.getWalWriter(cairoEngine.getTableTokenIfExists("statistics"))
  }

  private lazy val recordCursorFactory = {
    cairoEngine.select(QuestDbFEStatisticsRepository.selectQuery, ctx)
  }

  override def write(statistics: Map[String, Long])
                    (implicit ec: ExecutionContext): Future[Unit] = withExceptionHandling(
    () => {
      statistics.foreach { entry =>
        val row = statsWalTableWriter.newRow(Os.currentTimeMicros())
        row.putStr(0, entry._1)
        row.putLong(1, entry._2)
        row.append()
      }
      statsWalTableWriter.commit()
      // This job is assigned to the WorkerPool created in Server mode (standalone db)
      // (io.questdb.ServerMain.setupWalApplyJob, io.questdb.mp.Worker.run),
      // so it's basically assigned thread to run this job.
      // We will deal with that manually in our custom task to the periodic data flush.
      // This method will be run by the task.
      // TODO change to LazyList in scala 2.13
      Range.inclusive(1, 3).takeWhile(idx => applyWal2TableJob.run(1) && idx <= 3)
    },
    ()
  )

  override def read()(implicit ec: ExecutionContext): Future[Map[String, Long]] = withExceptionHandling(
    () => Using.resource(recordCursorFactory.getCursor(ctx)) { recordCursor =>
      val buffer = ArrayBuffer.empty[(String, Long)]
      val record = recordCursor.getRecord
      while (recordCursor.hasNext) {
        val name  = record.getStrA(0).toString
        val count = record.getLong(1)
        buffer.append(name -> count)
      }
      buffer.toMap
    },
    Map.empty[String, Long]
  )

  private def close(): Unit = {
    recordCursorFactory.close()
    statsWalTableWriter.close()
    applyWal2TableJob.close()
    cairoEngine.close()
  }

  private def initialize(): Unit =
    cairoEngine.ddl(createTableQuery, ctx)

  private def withExceptionHandling[T](dbAction: () => T, onError: => T)(
    implicit ec: ExecutionContext
  ): Future[T] =
    Future {
      Try(dbAction())
        .recover {
          case ex: CairoException if ex.isCritical =>
            logger.warn("Critical exception - recreating table")
            recreateTables()
            onError
          case ex: SqlException if ex.getMessage.contains("table does not exist") =>
            logger.warn("Table does not exists - recreating table")
            recreateTables()
            onError
          case ex =>
            logger.warn("DB exception", ex)
            onError
        }.get
    }

  private def recreateTables(): Unit = Try {
    logger.info("Recreating table")
    cairoEngine.drop(dropTablesQuery, ctx)
    QuestDbFEStatisticsRepository.createDirAndConfigureLogging()
    cairoEngine.ddl(createTableQuery, ctx)
  }.recover { case ex =>
    logger.warn("Exception occurred while tables recreate", ex)
    ex
  }

}

object QuestDbFEStatisticsRepository extends LazyLogging {

  def create(): Resource[IO, FEStatisticsRepository[Future]] =
    Resource.make(
      acquire = for {
        repository <- IO(configureAndBuild())
        _  <- IO(repository.initialize())
      } yield repository
    )(release = repository => IO(repository.close()))

  private def configureAndBuild(): QuestDbFEStatisticsRepository = {
    val nuDir  = createDirAndConfigureLogging()
    val engine = new CairoEngine(new CustomCairoConfiguration(nuDir.getAbsolutePath))
    new QuestDbFEStatisticsRepository(engine)
  }

  private def createDirAndConfigureLogging(): File = {
    val nuDir: File = createRootDirIfNotExists()
    configureLogging(nuDir)
    nuDir
  }

  private def createRootDirIfNotExists(): File = {
    val dir   = Try(Option(System.getProperty("java.io.tmpdir"))).toOption.flatten.getOrElse("/tmp")
    val nuDir = new File(dir + "/nu")
    nuDir.mkdirs()
    logger.debug("Statistics path: {}", nuDir)
    nuDir
  }

  private def configureLogging(rootDir: File): Unit = {
    LogFactory.configureRootDir(rootDir.getAbsolutePath)
    val logDir = new File(rootDir, "/conf")
    logDir.mkdirs()
    val logFile = new File(logDir, "log.conf")
    logFile.createNewFile()
    if (logFile.length() == 0) {
      Files.write(
        logFile.toPath,
        """
          |writers=stdout
          |w.stdout.class=io.questdb.log.LogConsoleWriter
          |w.stdout.level=ERROR
          |""".stripMargin.getBytes(StandardCharsets.UTF_8)
      )
    }
  }

  private val createTableQuery =
    "CREATE TABLE IF NOT EXISTS statistics (name string, count long, ts timestamp) TIMESTAMP(ts) PARTITION BY DAY WAL"

  private val selectQuery =
    s"""
       |   SELECT name,
       |          sum(count)
       |     FROM statistics
       |    WHERE date_trunc('day', ts) = date_trunc('day', now())
       | GROUP BY name""".stripMargin

  private val dropTablesQuery =
    "DROP ALL TABLES"

  private class CustomCairoConfiguration(private val root: String) extends DefaultCairoConfiguration(root) {

    override def getTelemetryConfiguration: TelemetryConfiguration = new TelemetryConfiguration {
      override def getDisableCompletely: Boolean = true
      override def getEnabled: Boolean           = false
      override def getQueueCapacity: Int         = 16
      override def hideTables(): Boolean         = false
    }

  }

}
