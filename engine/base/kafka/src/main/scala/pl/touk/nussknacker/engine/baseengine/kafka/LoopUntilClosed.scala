package pl.touk.nussknacker.engine.baseengine.kafka

import com.typesafe.scalalogging.LazyLogging

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{Executors, TimeUnit}
import scala.concurrent.duration.Duration
import scala.util.Using
import scala.util.control.NonFatal

//Runs task in loop, in several parallel copies restarting on errors
//TODO: probably there is some util for that? :)
class TaskRunner(taskParallelCount: Int, singleRun: () => Runnable with AutoCloseable, timeout: Duration) extends AutoCloseable {

  private val threadPool = Executors.newFixedThreadPool(taskParallelCount)

  private val tasks = (0 until taskParallelCount).map(_ => new LoopUntilClosed(singleRun))

  {
    tasks.foreach(threadPool.submit)
  }

  override def close(): Unit = {
    tasks.foreach(_.close())
    threadPool.shutdownNow()
    threadPool.awaitTermination(timeout.toSeconds, TimeUnit.SECONDS)
  }
}

class LoopUntilClosed(prepareSingleRunner: () => Runnable with AutoCloseable) extends Runnable with AutoCloseable with LazyLogging {

  private val closed = new AtomicBoolean(false)

  override def run(): Unit = {
    //we recreate runner until closed
    while (!closed.get()) {
      try {
        logger.info("Starting runner")
        Using.resource(prepareSingleRunner()) { singleRun =>
          //we loop until closed or exception occurs, then we close ourselves
          while (!closed.get()) {
            singleRun.run()
          }
        }
      } catch {
        case NonFatal(e) => logger.warn("Failed to run", e)
      }
    }
    logger.info("Finishing")
  }

  override def close(): Unit = {
    closed.set(true)
  }
}
