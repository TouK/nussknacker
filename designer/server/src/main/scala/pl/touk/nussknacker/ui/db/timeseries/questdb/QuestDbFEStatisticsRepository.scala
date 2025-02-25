package pl.touk.nussknacker.ui.db.timeseries.questdb

import org.apache.pekko.actor.{ActorSystem, Cancellable}
import better.files.File
import cats.effect.{IO, Resource}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.questdb.cairo.CairoEngine
import io.questdb.cairo.security.AllowAllSecurityContext
import io.questdb.cairo.sql.RecordCursorFactory
import io.questdb.cairo.wal.WalWriter
import io.questdb.griffin.{SqlExecutionContext, SqlExecutionContextImpl}
import pl.touk.nussknacker.ui.db.timeseries.questdb.QuestDbExtensions.{
  BuildCairoEngineExtension,
  CairoEngineExtension,
  RecordCursorFactoryExtension
}
import pl.touk.nussknacker.ui.db.timeseries.questdb.QuestDbFEStatisticsRepository.{
  createTableQuery,
  recreateCairoEngine,
  selectQuery,
  tableName
}
import pl.touk.nussknacker.ui.db.timeseries.{FEStatisticsRepository, NoOpFEStatisticsRepository}
import pl.touk.nussknacker.ui.statistics.RawFEStatistics

import java.time.Clock
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import java.util.concurrent.{ArrayBlockingQueue, ConcurrentHashMap, ThreadPoolExecutor, TimeUnit}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

private class QuestDbFEStatisticsRepository(private val engine: AtomicReference[CairoEngine], private val clock: Clock)(
    private implicit val ec: ExecutionContextExecutorService
) extends FEStatisticsRepository[Future]
    with LazyLogging {

  private val workerCount = 1

  private val sqlContextPool: ThreadAwareObjectPool[SqlExecutionContext] = new ThreadAwareObjectPool(() =>
    new SqlExecutionContextImpl(engine.get(), workerCount).`with`(AllowAllSecurityContext.INSTANCE, null)
  )

  private val recordCursorPool: ThreadAwareObjectPool[RecordCursorFactory] =
    new ThreadAwareObjectPool(() => engine.get().select(selectQuery, sqlContextPool.get()))

  private val walTableWriterPool: ThreadAwareObjectPool[WalWriter] = new ThreadAwareObjectPool(() =>
    engine.get().getWalWriter(engine.get().getTableTokenIfExists(tableName))
  )

  private val taskRecovery       = new TaskRecovery(engine, recover, tableName)
  private val flushDataTask      = new AtomicReference(createFlushDataTask())
  private val purgeWalTask       = new AtomicReference(createPurgeWalTask())
  private lazy val retentionTask = new AtomicReference(createRetentionTask())

  private val shouldCleanUpData    = new AtomicBoolean(false)
  private val aggregatedStatistics = new ConcurrentHashMap[String, java.lang.Long]()

  override def write(rawFEStatistics: RawFEStatistics): Future[Unit] = Future {
    rawFEStatistics.raw.foreach { case (key, newValue) =>
      aggregatedStatistics.compute(
        key,
        (_, oldValue) => {
          if (oldValue == null) {
            newValue
          } else {
            oldValue + newValue
          }
        }
      )
    }
  }

  override def read(): Future[RawFEStatistics] = Future {
    val result = recordCursorPool
      .get()
      .fetch(sqlContextPool.get()) { record =>
        val name  = record.getStrA(0).toString
        val count = record.getLong(1)
        name -> count
      }
      .toMap
    RawFEStatistics(result)
  }

  private def currentTimeMicros(): Long =
    Math.multiplyExact(clock.instant().toEpochMilli, 1000L)

  private def close(): Unit = {
    recordCursorPool.clear()
    walTableWriterPool.clear()
    sqlContextPool.clear()
    flushDataTask.get().close()
    purgeWalTask.get().close()
  }

  private def createTableIfNotExist(): Unit = {
    engine.get().ddl(createTableQuery, sqlContextPool.get())
  }

  private def recover(): Try[Unit] = synchronized {
    Try {
      close()
      recreateCairoEngine(engine)
      createTableIfNotExist()
      flushDataTask.set(createFlushDataTask())
      purgeWalTask.set(createPurgeWalTask())
      retentionTask.set(createRetentionTask())
    }
  }

  private def executeTasks(): Unit = {
    val statistics          = statisticsSnapshot()
    val currentTimeInMicros = currentTimeMicros()
    taskRecovery.runWithRecover(() => flushDataTask.get().runUnsafe(statistics, currentTimeInMicros))
    taskRecovery.runWithRecover(() => purgeWalTask.get().runUnsafe())
    runRetentionTask()
  }

  private def statisticsSnapshot(): Map[String, Long] = {
    aggregatedStatistics
      .keySet()
      .asScala
      .toList
      .map(key => key -> Try(aggregatedStatistics.remove(key).longValue()).getOrElse(0L))
      .toMap
  }

  private def runRetentionTask(): Unit = {
    if (shouldCleanUpData.get()) {
      shouldCleanUpData.set(false)
      taskRecovery.runWithRecover(() => retentionTask.get().runUnsafe())
    }
  }

  private def scheduleRetention(): Unit =
    shouldCleanUpData.set(true)

  private def createFlushDataTask() = new FlushDataTask(engine.get(), walTableWriterPool)
  private def createPurgeWalTask()  = new PurgeWalTask(engine.get())
  private def createRetentionTask() = new RetentionTask(engine.get(), tableName, sqlContextPool)
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

  def create(system: ActorSystem, clock: Clock, config: Config): Resource[IO, FEStatisticsRepository[Future]] =
    for {
      questDbConfig <- Resource.eval(IO(QuestDbConfig.apply(config)))
      repository <- questDbConfig match {
        case enabledCfg: QuestDbConfig.Enabled =>
          createRepositoryResource(system, clock, enabledCfg)
            .handleErrorWith { t: Throwable =>
              logger.warn("Creating QuestDb failed", t)
              createNoOpFEStatisticRepository
            }
        case QuestDbConfig.Disabled =>
          logger.debug("QuestDb is disabled - collecting FE statistics is skipped")
          createNoOpFEStatisticRepository
      }
    } yield repository

  private def createNoOpFEStatisticRepository: Resource[IO, FEStatisticsRepository[Future]] =
    Resource.pure[IO, FEStatisticsRepository[Future]](NoOpFEStatisticsRepository)

  private def createRepositoryResource(
      system: ActorSystem,
      clock: Clock,
      config: QuestDbConfig.Enabled
  ): Resource[IO, FEStatisticsRepository[Future]] =
    for {
      _               <- Resource.eval(IO(logger.debug(s"QuestDb configuration $config")))
      executorService <- createExecutorService(config.poolConfig)
      cairoEngine     <- createCairoEngine(config)
      repository      <- createRepository(executorService, cairoEngine, clock)
      _ <- scheduleTask(system, config.tasksExecutionDelay, config.tasksExecutionDelay, () => repository.executeTasks())
      _ <- scheduleTask(system, Duration.Zero, config.retentionDelay, () => repository.scheduleRetention())
    } yield repository

  private def createExecutorService(
      config: QuestDbConfig.QuestDbPoolConfig
  ): Resource[IO, ExecutionContextExecutorService] =
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

  private def createCairoEngine(config: QuestDbConfig.Enabled): Resource[IO, AtomicReference[CairoEngine]] =
    Resource.make(
      acquire = IO {
        val nuDir  = resolveRootDir(config)
        val engine = buildCairoEngine(nuDir)
        new AtomicReference(engine)
      }
    )(release = engine => IO(closeCairoEngine(engine.get())))

  private def resolveRootDir(config: QuestDbConfig.Enabled): File = {
    config.directory.map(d => File(d)).getOrElse(File.temp / s"nu/${config.instanceId}")
  }

  private def buildCairoEngine(rootDir: File): CairoEngine = {
    logger.debug("Statistics path: {}", rootDir)
    val canonicalPath = rootDir
      .createDirIfNotExists()
      .configureLogging()
      .canonicalPath
    new CairoEngine(new CustomCairoConfiguration(canonicalPath))
  }

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
  )(release =
    repository =>
      IO {
        repository.executeTasks()
        repository.close()
      }
  )

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
    val rootDir = cairoEngine.deleteRootDir()
    engine.set(buildCairoEngine(rootDir))
  }

}
