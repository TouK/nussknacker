package pl.touk.nussknacker.ui.db.timeseries.questdb

import akka.actor.{ActorSystem, Cancellable}
import better.files.File
import cats.effect.{IO, Resource}
import com.typesafe.scalalogging.LazyLogging
import io.questdb.cairo.security.AllowAllSecurityContext
import io.questdb.cairo.{CairoEngine, CairoException}
import io.questdb.griffin.{SqlException, SqlExecutionContextImpl}
import io.questdb.log.LogFactory
import pl.touk.nussknacker.ui.db.timeseries.FEStatisticsRepository
import pl.touk.nussknacker.ui.db.timeseries.questdb.QuestDbExtensions.{
  CairoEngineExtension,
  RecordCursorFactoryExtension
}
import pl.touk.nussknacker.ui.db.timeseries.questdb.QuestDbFEStatisticsRepository.{
  createTableQuery,
  recover,
  selectQuery,
  tableName
}

import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import java.util.concurrent.{ArrayBlockingQueue, ThreadPoolExecutor, TimeUnit}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future}
import scala.util.{Failure, Success, Try}

// TODO list:
// WARNING - for now is not thread safe - multiple request at the same time will kill application
// 2. Compacting?
// 4. Configurable directory of db (for now it's tmp directory) and limiting db file space on disk.
// 5. Collecting statistics should be deactivable by configuration. (If db creation fails provide NoOp DB as fallback)
// 6. API should have better types (missing domain layer for FE statistics names).
// 6. Handling errors while creating the QuestDb.
// 7. Changing table definition and recreate
// 9. multi tenant metrics -> add todo that if there are many instances we should change diagrams on grafana
// 10. flush data in close?
// todo polish this class in next commits
private class QuestDbFEStatisticsRepository(private val engine: AtomicReference[CairoEngine], private val clock: Clock)(
    private implicit val ec: ExecutionContextExecutorService
) extends FEStatisticsRepository[Future]
    with LazyLogging {

  private val sqlContext = new ThreadAwareObjectPool(() =>
    new SqlExecutionContextImpl(engine.get(), 1).`with`(AllowAllSecurityContext.INSTANCE, null)
  )

  private val recordCursorPool = new ThreadAwareObjectPool(() => engine.get().select(selectQuery, sqlContext.get()))

  private val walTableWriterPool = new ThreadAwareObjectPool(() =>
    engine.get().getWalWriter(engine.get().getTableTokenIfExists(tableName))
  )

  private val flushDataTask      = new AtomicReference(createFlushDataTask())
  private lazy val retentionTask = new AtomicReference(createRetentionTask())
  private val shouldCleanUpData  = new AtomicBoolean(false)

  override def write(statistics: Map[String, Long]): Future[Unit] = withExceptionHandling(() => {
    val statsWalTableWriter = walTableWriterPool.get()
    statistics.foreach { entry =>
      val row = statsWalTableWriter.newRow(currentTimeMicros())
      row.putStr(0, entry._1)
      row.putLong(1, entry._2)
      row.append()
    }
    statsWalTableWriter.commit()
  })

  override def read(): Future[Map[String, Long]] = withExceptionHandling(() => {
    recordCursorPool
      .get()
      .fetch(sqlContext.get()) { record =>
        val name  = record.getStrA(0).toString
        val count = record.getLong(1)
        name -> count
      }
      .toMap
  })

  private def currentTimeMicros(): Long =
    Math.multiplyExact(clock.instant().toEpochMilli, 1000L)

  private def close(): Unit = {
    recordCursorPool.clear()
    walTableWriterPool.clear()
    sqlContext.clear()
    flushDataTask.get().close()
    retentionTask.get().close()
  }

  private def initialize(): Unit = {
    engine.get().ddl(createTableQuery, sqlContext.get())
  }

  private def withExceptionHandling[T](dbAction: () => T): Future[T] =
    Future {
      if (engine.get().tableExists(tableName)) {
        runDbAction(dbAction)
      } else {
        recoverWithRetry(new IllegalStateException("Table does not exist"), dbAction).get
      }
    }

  private def runDbAction[T](dbAction: () => T) = {
    Try(dbAction()).recoverWith {
      case ex: CairoException if ex.isCritical || ex.isTableDropped =>
        recoverWithRetry(ex, dbAction)
      case ex: SqlException if ex.getMessage.contains("table does not exist") =>
        recoverWithRetry(ex, dbAction)
      case ex =>
        logger.warn("DB exception", ex)
        Failure(ex)
    }.get
  }

  private def recoverWithRetry[T](ex: Exception, dbAction: () => T): Try[T] = {
    logger.warn("Statistic DB exception - trying to recover", ex)
    closeAndRecover() match {
      case Failure(ex) =>
        logger.warn("Exception occurred while tables recreate", ex)
        Failure(ex)
      case Success(_) =>
        logger.info("Recreate succeeded - retrying db action")
        Try(dbAction()) match {
          case f @ Failure(ex) =>
            logger.warn("Exception occurred during db retry", ex)
            f
          case s @ Success(_) =>
            logger.info("Retried db action succeeded")
            s
        }
    }
  }

  private def closeAndRecover(): Try[Unit] = Try {
    recordCursorPool.clear()
    walTableWriterPool.clear()
    sqlContext.clear()
    recover(engine)
    initialize()
    flushDataTask.set(createFlushDataTask())
    retentionTask.set(createRetentionTask())
  }

  private def flushDataToDisk(): Unit = {
    flushDataTask.get().run()
    if (shouldCleanUpData.get()) {
      cleanUpOldData()
    }
  }

  private def cleanUpOldData(): Unit = Try {
    shouldCleanUpData.set(false)
    retentionTask.get().run(sqlContext.get(), clock)
  }.recover { case ex: Exception =>
    logger.warn("Exception thrown while cleaning data.", ex)
  }

  private def scheduleRetention(): Unit =
    shouldCleanUpData.set(true)

  private def createFlushDataTask() = new FlushDataTask(engine.get(), tableName)
  private def createRetentionTask() = new RetentionTask(engine.get(), tableName)
}

object QuestDbFEStatisticsRepository extends LazyLogging {
  private val tableName = "fe_statistics"
  private val createTableQuery =
    s"CREATE TABLE IF NOT EXISTS $tableName (name string, count long, ts timestamp) TIMESTAMP(ts) PARTITION BY DAY WAL"

  private val selectQuery =
    s"""
       |   SELECT name,
       |          sum(count)
       |     FROM $tableName
       |    WHERE timestamp_floor('d', ts) = timestamp_floor('d', now())
       | GROUP BY name""".stripMargin

  def create(system: ActorSystem, clock: Clock): Resource[IO, FEStatisticsRepository[Future]] = for {
    executorService <- createExecutorService()
    cairoEngine     <- createCairoEngine()
    repository      <- createRepository(executorService, cairoEngine, clock)
    // TODO: move task properties to configuration
    _ <- createTask(system, Duration(2L, TimeUnit.SECONDS), () => repository.flushDataToDisk())
    _ <- createTask(system, Duration(2L, TimeUnit.SECONDS), () => repository.scheduleRetention())
  } yield repository

  // todo move this ExecutorService properties to a configuration
  // todo log if task rejected
  private def createExecutorService(): Resource[IO, ExecutionContextExecutorService] = Resource.make(
    acquire = for {
      executorService <- IO(new ThreadPoolExecutor(2, 4, 60L, TimeUnit.SECONDS, new ArrayBlockingQueue[Runnable](8)))
      ec = ExecutionContext.fromExecutorService(executorService)
    } yield ec
  )(release = ec => IO(ec.shutdown()))

  private def createCairoEngine(): Resource[IO, AtomicReference[CairoEngine]] = Resource.make(
    acquire = IO(
      new AtomicReference(
        buildCairoEngine(createDirAndConfigureLogging())
      )
    )
  )(release = engine => IO(closeCairoEngine(engine.get())))

  private def buildCairoEngine(rootDir: File): CairoEngine =
    new CairoEngine(new CustomCairoConfiguration(rootDir.canonicalPath))

  private def closeCairoEngine(engine: CairoEngine): Unit =
    Try(engine.close()) match {
      // Due to: /io/questdb/cairo/pool/AbstractMultiTenantPool.java:347
      case Failure(_) => engine.close()
      case Success(_) =>
    }

  private def createRepository(
      ec: ExecutionContextExecutorService,
      cairoEngine: AtomicReference[CairoEngine],
      clock: Clock
  ): Resource[IO, QuestDbFEStatisticsRepository] = Resource.make(
    acquire = for {
      repository <- IO(new QuestDbFEStatisticsRepository(cairoEngine, clock)(ec))
      _          <- IO(repository.initialize())
    } yield repository
  )(release = repository => IO(repository.close()))

  private def createDirAndConfigureLogging(): File = {
    val nuDir: File = createRootDirIfNotExists()
    configureLogging(nuDir)
    nuDir
  }

  private def createRootDirIfNotExists(): File = {
    val nuDir = File.temp.createChild("nu", asDirectory = true)
    logger.debug("Statistics path: {}", nuDir)
    nuDir
  }

  private def configureLogging(rootDir: File): Unit = {
    rootDir
      .createChild("conf/log.conf", createParents = true)
      .writeText(
        """
          |writers=stdout
          |w.stdout.class=io.questdb.log.LogConsoleWriter
          |w.stdout.level=ERROR
          |""".stripMargin
      )(Seq(StandardOpenOption.TRUNCATE_EXISTING), StandardCharsets.UTF_8)
    LogFactory.configureRootDir(rootDir.canonicalPath)
  }

  private def createTask(system: ActorSystem, interval: FiniteDuration, runnable: Runnable): Resource[IO, Cancellable] =
    Resource.make(
      acquire = IO(
        system.scheduler
          .scheduleWithFixedDelay(interval, interval)(runnable)(system.dispatcher)
      )
    )(release = task => IO(task.cancel()))

  private def recover(engine: AtomicReference[CairoEngine]): Unit = this.synchronized {
    val cairoEngine = engine.get()
    closeCairoEngine(cairoEngine)
    val nuDir = File(cairoEngine.getConfiguration.getRoot)
    if (nuDir.exists) {
      Try(nuDir.delete())
    }
    createDirAndConfigureLogging()
    engine.set(buildCairoEngine(nuDir))
  }

}
