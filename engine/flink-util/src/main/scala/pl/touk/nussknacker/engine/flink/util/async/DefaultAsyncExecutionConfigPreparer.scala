package pl.touk.nussknacker.engine.flink.util.async

import java.util.concurrent._
import java.util.concurrent.atomic.AtomicLong

import org.apache.commons.lang3.concurrent.BasicThreadFactory
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.process.AsyncExecutionContextPreparer

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

//TODO: this is somewhat experimental - how should we behave??
object DefaultAsyncExecutionConfigPreparer {

  private final var asyncExecutionContext : Option[ExecutionContextExecutorService] = None

  private final val counter = new AtomicLong(0)

  private val executorServiceCreator: (Int, ThreadFactory) => ExecutorService =
    (workers, threadFactory) => new ThreadPoolExecutor(workers, workers, 1, TimeUnit.MINUTES, new LinkedBlockingQueue[Runnable], threadFactory)


  private[DefaultAsyncExecutionConfigPreparer] def getExecutionContext(workers: Int, process: String) = synchronized {
    counter.incrementAndGet()
    asyncExecutionContext match {
      case Some(ec) => ec
      case None =>
        val threadFactory = new BasicThreadFactory.Builder().namingPattern(s"asyncWorkerThread-$process-%d").build()
        val ec = ExecutionContext.fromExecutorService(executorServiceCreator(workers, threadFactory))
        asyncExecutionContext = Some(ec)
        ec
    }
  }

  private[DefaultAsyncExecutionConfigPreparer] def close(): Unit = {
    if (counter.decrementAndGet() == 0) {
      asyncExecutionContext.foreach(_.shutdownNow())
      asyncExecutionContext = None
    }
  }

}

case class DefaultAsyncExecutionConfigPreparer(bufferSize: Int, parallelismMultiplier: Int) extends AsyncExecutionContextPreparer {

  def prepareExecutionContext(processId: String) : ExecutionContext = {
    DefaultAsyncExecutionConfigPreparer.getExecutionContext(StreamExecutionEnvironment.getExecutionEnvironment.getParallelism * parallelismMultiplier, processId)
  }

  def close() : Unit = {
    DefaultAsyncExecutionConfigPreparer.close()
  }

}
