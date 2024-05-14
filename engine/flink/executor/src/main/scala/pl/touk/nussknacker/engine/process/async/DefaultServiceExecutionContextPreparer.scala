package pl.touk.nussknacker.engine.process.async

import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import pl.touk.nussknacker.engine.api.process.{AsyncExecutionContextPreparer, ProcessName, ServiceExecutionContext}

import java.util.concurrent._
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

//TODO: this is somewhat experimental - how should we behave??
object DefaultServiceExecutionContextPreparer extends LazyLogging {

  private final var asyncExecutionContext: Option[(ProcessName, ExecutionContextExecutorService)] = None

  private val executorServiceCreator: (Int, ThreadFactory) => ExecutorService =
    (workers, threadFactory) => {
      val ex =
        new ThreadPoolExecutor(workers, workers, 1, TimeUnit.MINUTES, new LinkedBlockingQueue[Runnable], threadFactory)
      // it seems with async timeout this pool can be not closed during process restart. To avoid thread leak we want to timeout also core threads
      ex.allowCoreThreadTimeOut(true)
      ex
    }

  private[DefaultServiceExecutionContextPreparer] def getExecutionContext(
      workers: Int,
      processName: ProcessName
  ): ServiceExecutionContext = synchronized {
    logger.info(s"Creating asyncExecutor for $processName, with $workers workers")
    ServiceExecutionContext {
      asyncExecutionContext match {
        case Some((_, ec)) => ec
        case None =>
          val threadFactory =
            new BasicThreadFactory.Builder().namingPattern(s"asyncWorkerThread-$processName-%d").build()
          val ec = ExecutionContext.fromExecutorService(executorServiceCreator(workers, threadFactory))
          asyncExecutionContext = Some((processName, ec))
          ec
      }
    }
  }

  private[DefaultServiceExecutionContextPreparer] def close(): Unit = synchronized {
    logger.info(s"Closing asyncExecutor for ${asyncExecutionContext.map(_._1)}")
    asyncExecutionContext.foreach { case (_, executorService) => executorService.shutdownNow() }
    asyncExecutionContext = None
  }

}

final case class DefaultServiceExecutionContextPreparer(
    bufferSize: Int,
    workers: Int,
    defaultUseAsyncInterpretation: Option[Boolean]
) extends AsyncExecutionContextPreparer
    with LazyLogging {

  def prepare(processName: ProcessName): ServiceExecutionContext = {
    logger.info(s"Creating asyncExecutor for $processName, workers: $workers")
    DefaultServiceExecutionContextPreparer.getExecutionContext(workers, processName)
  }

  def close(): Unit = {
    DefaultServiceExecutionContextPreparer.close()
  }

}
