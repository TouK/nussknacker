package pl.touk.nussknacker.engine.baseengine.kafka

import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import org.apache.kafka.common.errors.InterruptException

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{Callable, CompletableFuture, Executors, TimeUnit}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.compat.java8.FutureConverters._
import scala.util.control.NonFatal

//Runs task in loop, in several parallel copies restarting on errors
//TODO: probably there is some util for that? :)
class TaskRunner(taskName: String,
                 taskParallelCount: Int,
                 singleRun: String => Task,
                 terminationTimeout: Duration) extends AutoCloseable with LazyLogging {

  private val threadFactory = new BasicThreadFactory.Builder()
    .namingPattern(s"worker-$taskName-%d")
    .build()

  private val threadPool = Executors.newFixedThreadPool(taskParallelCount, threadFactory)

  private val tasks: List[LoopUntilClosed] = (0 until taskParallelCount).map(idx => new LoopUntilClosed(() => singleRun(s"task-$idx"))).toList

  def run(implicit ec: ExecutionContext): Future[Unit] = {
    Future.sequence(runAllTasks()).map(_ => ())
  }

  /*
    This is a bit tricky, we split the run method as we have to use two different ExecutionContextes:
    - one is backed by fixed threadPool and runs Tasks
    - the other (passed in run()) method is used to sequence over list of Futures and do final mapping
   */
  private def runAllTasks(): List[Future[Unit]] = {
    val ecForRunningTasks = ExecutionContext.fromExecutor(threadPool)
    tasks.map { task =>
      Future {
        task.run()
      }(ecForRunningTasks)
    }
  }

  override def close(): Unit = {
    tasks.foreach(_.close())
    logger.debug("Tasks notified of closure, closing thread pool...")
    threadPool.shutdownNow()
    val terminatedSuccessfully = threadPool.awaitTermination(terminationTimeout.toSeconds, TimeUnit.SECONDS)
    if (terminatedSuccessfully) {
      logger.info("Thread pool terminated successfully")
    } else {
      logger.error("Thread pool termination timeout")
    }
  }
}

//Assumptions: run will be invoked only after successful init, close will be invoked if init fails
trait Task extends Runnable with AutoCloseable {
  def init(): Unit
}

class LoopUntilClosed(prepareSingleRunner: () => Task) extends Runnable with AutoCloseable with LazyLogging {

  private val closed = new AtomicBoolean(false)

  override def run(): Unit = {
    //we recreate runner until closed
    while (!closed.get()) {
      logger.info("Starting runner")
      handleOneRunLoop()
    }
    logger.info("Finishing runner")
  }

  //We don't use Using.resources etc. because we want to treat throwing in .close() differently - this should be propagated
  //and handled differently as it leads to resource leak, so we'll let uncaughtExceptionHandler deal with that
  private def handleOneRunLoop(): Unit = {
    val singleRun = prepareSingleRunner()
    try {
      singleRun.init()
      //we loop until closed or exception occurs, then we close ourselves
      while (!closed.get()) {
        singleRun.run()
      }
    } catch {
      /*
        After setting closed = true, we close pool, which interrupts all threads.
        In most cases Interrupt(ed)Exception will be thrown - either from Await.result or consumer.poll (in second case it'll be wrapped)
        We want to ignore it and proceed with normal closing - otherwise there will be errors in closing consumer
       */
      case _: InterruptedException | _: InterruptException if closed.get() =>
        //This is important - as it's the only way to clear interrupted flag...
        val wasInterrupted = Thread.interrupted()
        logger.debug(s"Interrupted: $wasInterrupted, finishing normally")
      case NonFatal(e) =>
        logger.warn("Failed to run", e)
    } finally {
      singleRun.close()
    }
  }

  override def close(): Unit = {
    closed.set(true)
  }
}
