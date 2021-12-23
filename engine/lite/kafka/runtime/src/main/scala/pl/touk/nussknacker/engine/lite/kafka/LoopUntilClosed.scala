package pl.touk.nussknacker.engine.lite.kafka

import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.lang3.concurrent.BasicThreadFactory
import org.apache.kafka.common.errors.InterruptException
import pl.touk.nussknacker.engine.lite.kafka.TaskStatus.{Restarting, Running, TaskStatus}

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

//Runs task in loop, in several parallel copies restarting on errors
//TODO: probably there is some util for that? :)
class TaskRunner(taskName: String,
                 taskParallelCount: Int,
                 singleRun: String => Task,
                 terminationTimeout: Duration,
                 waitAfterFailureDelay: FiniteDuration) extends AutoCloseable with LazyLogging {
  def status(): TaskStatus = tasks.find(p => p.status() == Restarting).map(_.status()).getOrElse(Running)

  private val threadFactory = new BasicThreadFactory.Builder()
    .namingPattern(s"worker-$taskName-%d")
    .build()

  private val threadPool = Executors.newFixedThreadPool(taskParallelCount, threadFactory)

  private val tasks: List[LoopUntilClosed] = (0 until taskParallelCount).map(idx => new LoopUntilClosed(() => singleRun(s"task-$idx"), waitAfterFailureDelay)).toList

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

object TaskStatus extends Enumeration {
  type TaskStatus = Value
  val Running: Value = Value("RUNNING")
  val Restarting: Value = Value("RESTARTING")
}

//Assumptions: run will be invoked only after successful init, close will be invoked if init fails
trait Task extends Runnable with AutoCloseable {
  def init(): Unit
}

class LoopUntilClosed(prepareSingleRunner: () => Task, waitAfterFailureDelay: FiniteDuration) extends Runnable with AutoCloseable with LazyLogging {

  private val closed = new AtomicBoolean(false)
  private val restarting = new AtomicBoolean(false)

  def status(): TaskStatus = {
    if (restarting.get()) {
      Restarting
    } else {
      Running
    }
  }

  override def run(): Unit = {
    //we recreate runner until closed
    var attempt = 1
    var previousErrorWithTimestamp = Option.empty[(Throwable, Long)]
    while (!closed.get()) {
      val wasFailureDuringSleep = handleSleepBeforeRestart(previousErrorWithTimestamp).exists(_.isFailure)
      // in case of failure during sleep we should check main loop condition again instead of initializing run again
      if (!wasFailureDuringSleep) {
        logger.info(s"Starting runner, attempt: $attempt")
        previousErrorWithTimestamp = handleOneRunLoop().failed.toOption.map((_, System.currentTimeMillis()))
        attempt += 1
      }
    }
    logger.info("Finishing runner")
  }

  private def handleSleepBeforeRestart(previousErrorWithTimestamp: Option[(Throwable, Long)]): Option[Try[Unit]] = {
    previousErrorWithTimestamp.map {
      case (e, failureTimestamp) =>
        val delayToWait = failureTimestamp + waitAfterFailureDelay.toMillis - System.currentTimeMillis()
        if (delayToWait > 0) {
          logger.warn(s"Failed to run. Waiting: $delayToWait millis to restart...", e)
          tryWithInterruptedHandle {
            restarting.set(true)
            Thread.sleep(delayToWait)
          } {
            restarting.set(false)
          }
        } else {
          logger.warn(s"Failed to run. Restarting...", e)
          Success(Unit)
        }
    }
  }

  //We don't use Using.resources etc. because we want to treat throwing in .close() differently - this should be propagated
  //and handled differently as it leads to resource leak, so we'll let uncaughtExceptionHandler deal with that
  private def handleOneRunLoop(): Try[Unit] = {
    val singleRun = prepareSingleRunner()
    tryWithInterruptedHandle {
      singleRun.init()
      //we loop until closed or exception occurs, then we close ourselves
      while (!closed.get()) {
        singleRun.run()
      }
    } {
      singleRun.close()
    }
  }

  private def tryWithInterruptedHandle(runWithSomeWaiting: => Unit)
                                      (handleFinally: => Unit): Try[Unit] = {
    try {
      runWithSomeWaiting
      Success(Unit)
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
        Success(Unit)
      case NonFatal(e) =>
        Failure(e)
    } finally {
      handleFinally
    }
  }

  override def close(): Unit = {
    closed.set(true)
  }
}
