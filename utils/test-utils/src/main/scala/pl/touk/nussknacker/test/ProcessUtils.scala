package pl.touk.nussknacker.test

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers

import java.io.{BufferedReader, InputStream, InputStreamReader}
import scala.concurrent.{blocking, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

object ProcessUtils extends LazyLogging with Matchers with VeryPatientScalaFutures {

  // Some applications, e.g. Java, exit with 128+cause, so SIGTERM (15) results in 128+15=143
  private val successExitCodes: Set[Int] = Set(0, 143)

  def attachLoggingAndReturnWaitingFuture(name: String, process: Process): Future[Int] = {
    logger.info(s"Started process: ${process.info()} with pid: ${process.pid()}")
    process.onExit().thenApply[Unit] { p =>
      if (successExitCodes.contains(p.exitValue())) {
        logger.info(s"Process exited with success ${process.exitValue()} exit code")
      } else {
        logger.warn(s"Process exited with failure ${process.exitValue()} exit code")
      }
    }
    val logPrefix = s"[$name:${process.pid()}] "
    streamLines(process.getInputStream, { line => System.out.println(logPrefix + line) })
    streamLines(process.getErrorStream, { line => System.err.println(logPrefix + line) })
    val future = Future {
      blocking {
        process.waitFor()
      }
    }
    future.failed.foreach { ex =>
      logger.error("process.waitFor() interrupted", ex)
    }
    future
  }

  def checkIfFailedInstantly(exitCodeFuture: Future[Int]): Unit = {
    exitCodeFuture.value.foreach { tryValue =>
      // If completed with failure instantly, fail to not shadow true failure by further checks
      tryValue.failed.toOption shouldBe empty
      tryValue.toOption.filter(exitCode => !successExitCodes.contains(exitCode)) shouldBe empty
    }
  }

  def checkIfSucceedEventually(exitCodeFuture: Future[Int]): Assertion = {
    successExitCodes should contain(exitCodeFuture.futureValue)
  }

  def destroyProcessEventually[T](process: Process)(run: => T): T = {
    try {
      run
    } catch {
      case NonFatal(ex) =>
        if (process.isAlive) {
          logger.info("Some exception occurred. Process is still alive - dumping threads")
          // thread dump
          Runtime.getRuntime.exec(s"kill -3 ${process.pid()}")
          // wait a while to make sure that stack trace is presented in logs
          Thread.sleep(3000)
        }
        throw ex
    } finally {
      if (process.isAlive) {
        process.destroy()
      }
    }
  }

  private def streamLines(inputStream: InputStream, printFn: String => Unit): Future[Unit] =
    Future {
      blocking {
        val reader = new BufferedReader(new InputStreamReader(inputStream))
        try {
          Iterator
            .continually(reader.readLine())
            .takeWhile(_ != null)
            .foreach(line => printFn(line))
        } finally {
          reader.close()
        }
      }
    }

}
