package pl.touk.nussknacker.engine.flink.util.async

import java.util.concurrent._
import java.util.concurrent.atomic.AtomicLong

import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import pl.touk.nussknacker.engine.api.process.AsyncExecutionContextPreparer

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

//TODO: this is somewhat experimental - how should we behave??
object DefaultAsyncExecutionConfigPreparer extends LazyLogging {

  private final var asyncExecutionContext : Option[(String, ExecutionContextExecutorService)] = None

  private final val counter = new AtomicLong(0)

  private val executorServiceCreator: (Int, ThreadFactory) => ExecutorService =
    (workers, threadFactory) => {
      val ex = new ThreadPoolExecutor(workers, workers, 1, TimeUnit.MINUTES, new LinkedBlockingQueue[Runnable], threadFactory)
      //it seems with async timeout this pool can be not closed during process restart. To avoid thread leak we want to timeout also core threads
      ex.allowCoreThreadTimeOut(true)
      ex
    }


  private[DefaultAsyncExecutionConfigPreparer] def getExecutionContext(workers: Int, process: String) = synchronized {
    counter.incrementAndGet()
    logger.info(s"Creating asyncExecutor for $process, with $workers workers, counter is ${counter.get()}")
    asyncExecutionContext match {
      case Some((_, ec)) => ec
      case None =>
        val threadFactory = new BasicThreadFactory.Builder().namingPattern(s"asyncWorkerThread-$process-%d").build()
        val ec = ExecutionContext.fromExecutorService(executorServiceCreator(workers, threadFactory))
        asyncExecutionContext = Some((process, ec))
        ec
    }
  }

  private[DefaultAsyncExecutionConfigPreparer] def close(): Unit = {
    logger.info(s"Closing asyncExecutor for ${asyncExecutionContext.map(_._1)} counter is ${counter.get()}")
    if (counter.decrementAndGet() == 0) {
      asyncExecutionContext.foreach(_._2.shutdownNow())
      asyncExecutionContext = None
    }
  }

}

case class DefaultAsyncExecutionConfigPreparer(bufferSize: Int,
                                               workers: Int,
                                               defaultUseAsyncInterpretation: Option[Boolean]) extends AsyncExecutionContextPreparer with LazyLogging {

  def prepareExecutionContext(processId: String, parallelism: Int) : ExecutionContext = {
    logger.info(s"Creating asyncExecutor for $processId, parallelism: $parallelism, workers: $workers")
    DefaultAsyncExecutionConfigPreparer.getExecutionContext(workers, processId)
  }

  def close() : Unit = {
    DefaultAsyncExecutionConfigPreparer.close()
  }

}
