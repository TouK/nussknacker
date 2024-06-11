package pl.touk.nussknacker.ui.db.timeseries.questdb

import akka.actor.{ActorSystem, Cancellable}
import better.files.File
import cats.effect.{IO, Resource}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.questdb.cairo.CairoEngine
import io.questdb.cairo.security.AllowAllSecurityContext
import io.questdb.griffin.SqlExecutionContextImpl
import io.questdb.log.LogFactory
import pl.touk.nussknacker.ui.db.timeseries.FEStatisticsRepository
import pl.touk.nussknacker.ui.db.timeseries.questdb.QuestDbExtensions.{
  CairoEngineExtension,
  RecordCursorFactoryExtension
}
import pl.touk.nussknacker.ui.db.timeseries.questdb.QuestDbFEStatisticsRepository.{
  createTableQuery,
  recreateCairoEngine,
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
// 2. Compacting?
// 4. Limiting db file space on disk?
// 6. API should have better types (missing domain layer for FE statistics names).
// 7. Changing table definition and recreate
// 10. flush data in close?
// todo polish this class in next commits
private class QuestDbFEStatisticsRepository(private val engine: AtomicReference[CairoEngine], private val clock: Clock)(
    private implicit val ec: ExecutionContextExecutorService
) extends FEStatisticsRepository[Future]
    with LazyLogging {

  private val workerCount = 1

  private val sqlContextPool = new ThreadAwareObjectPool(() =>
    new SqlExecutionContextImpl(engine.get(), workerCount).`with`(AllowAllSecurityContext.INSTANCE, null)
  )

  private val recordCursorPool = new ThreadAwareObjectPool(() => engine.get().select(selectQuery, sqlContextPool.get()))

  private val walTableWriterPool = new ThreadAwareObjectPool(() =>
    engine.get().getWalWriter(engine.get().getTableTokenIfExists(tableName))
  )

  private val flushDataTask      = new AtomicReference(createFlushDataTask())
  private lazy val retentionTask = new AtomicReference(createRetentionTask())
  private val shouldCleanUpData  = new AtomicBoolean(false)

  override def write(statistics: Map[String, Long]): Future[Unit] = run(() => {
    val statsWalTableWriter = walTableWriterPool.get()
    statistics.foreach { entry =>
      val row = statsWalTableWriter.newRow(currentTimeMicros())
      row.putStr(0, entry._1)
      row.putLong(1, entry._2)
      row.append()
    }
    statsWalTableWriter.commit()
  })

  override def read(): Future[Map[String, Long]] = run(() => {
    recordCursorPool
      .get()
      .fetch(sqlContextPool.get()) { record =>
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
    sqlContextPool.clear()
    flushDataTask.get().close()
    retentionTask.get().close()
  }

  private def createTableIfNotExist(): Unit = {
    engine.get().ddl(createTableQuery, sqlContextPool.get())
  }

  private def run[T](dbAction: () => T): Future[T] =
    Future {
      engine.get().runWithExceptionHandling(recover, tableName, dbAction)
    }

  private def recover(): Try[Unit] = synchronized {
    Try {
      close()
      recreateCairoEngine(engine)
      createTableIfNotExist()
      flushDataTask.set(createFlushDataTask())
      retentionTask.set(createRetentionTask())
    }
  }

  private def flushDataToDisk(): Unit = {
    flushDataTask.get().run()
    if (shouldCleanUpData.get()) {
      cleanUpOldData()
    }
  }

  private def cleanUpOldData(): Unit = Try {
    shouldCleanUpData.set(false)
    retentionTask.get().run(sqlContextPool.get(), clock)
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

  def create(system: ActorSystem, clock: Clock, config: QuestDbConfig): Resource[IO, FEStatisticsRepository[Future]] =
    for {
      _               <- Resource.eval(IO(logger.info(s"QuestDb configuration $config")))
      executorService <- createExecutorService(config.poolConfig)
      cairoEngine     <- createCairoEngine(config)
      repository      <- createRepository(executorService, cairoEngine, clock)
      _ <- scheduleTask(system, config.flushTaskDelay, config.flushTaskDelay, () => repository.flushDataToDisk())
      _ <- scheduleTask(system, Duration.Zero, config.retentionTaskDelay, () => repository.scheduleRetention())
    } yield repository

  private def createExecutorService(config: QuestDbPoolConfig): Resource[IO, ExecutionContextExecutorService] =
    Resource.make(
      acquire = for {
        executorService <- IO(
          new ThreadPoolExecutor(
            config.corePoolSize,
            config.maxPoolSize,
            config.keepAliveTimeInSeconds,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue[Runnable](config.queueCapacity)
          )
        )
        ec = ExecutionContext.fromExecutorService(executorService)
      } yield ec
    )(release = ec => IO(ec.shutdown()))

  private def createCairoEngine(config: QuestDbConfig): Resource[IO, AtomicReference[CairoEngine]] =
    Resource.make(
      acquire = IO {
        val nuDir = resolveRootDir(config)
        createRootDirIfNotExists(nuDir)
        configureLogging(nuDir)
        val engine = buildCairoEngine(nuDir)
        new AtomicReference(engine)
      }
    )(release = engine => IO(closeCairoEngine(engine.get())))

  private def resolveRootDir(config: QuestDbConfig): File = {
    config.directory.map(d => File(d)).getOrElse(File.temp / s"nu/${config.instanceId}")
  }

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
      _          <- IO(repository.createTableIfNotExist())
    } yield repository
  )(release = repository => IO(repository.close()))

  private def createRootDirIfNotExists(nuDir: File): Unit = {
    nuDir.createDirectories()
    logger.debug("Statistics path: {}", nuDir)
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

  private def scheduleTask(
      system: ActorSystem,
      initialDelay: FiniteDuration,
      interval: FiniteDuration,
      runnable: Runnable
  ): Resource[IO, Cancellable] =
    Resource.make(
      acquire = IO(
        system.scheduler
          .scheduleWithFixedDelay(initialDelay, interval)(runnable)(system.dispatcher)
      )
    )(release = task => IO(task.cancel()))

  private def recreateCairoEngine(engine: AtomicReference[CairoEngine]): Unit = {
    val cairoEngine = engine.get()
    closeCairoEngine(cairoEngine)
    val nuDir = File(cairoEngine.getConfiguration.getRoot)
    if (nuDir.exists) {
      Try(nuDir.delete())
    }
    createRootDirIfNotExists(nuDir)
    configureLogging(nuDir)
    engine.set(buildCairoEngine(nuDir))
  }

}
