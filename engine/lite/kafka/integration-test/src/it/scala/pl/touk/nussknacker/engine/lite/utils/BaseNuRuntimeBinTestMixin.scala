package pl.touk.nussknacker.engine.lite.utils

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.TestSuite
import org.scalatest.matchers.should.Matchers
import org.springframework.util.StreamUtils
import pl.touk.nussknacker.test.VeryPatientScalaFutures

import java.io.IOException
import java.nio.file.Path
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.concurrent.ExecutionContext.Implicits.global

trait BaseNuRuntimeBinTestMixin extends VeryPatientScalaFutures with Matchers { self: TestSuite with LazyLogging =>

  protected val akkaManagementEnvs: Array[String] = Array(
    // random management port to avoid clashing of ports
    "CONFIG_FORCE_akka_management_http_port=0",
    // It looks like github-actions doesn't look binding to 0.0.0.0, was problems like: Bind failed for TCP channel on endpoint [/10.1.0.183:0]
    "CONFIG_FORCE_akka_management_http_hostname=127.0.0.1"
  )

  protected def withProcessExecutedInBackground(shellScriptArgs: Array[String], shellScriptEnvs: Array[String],
                                                executeBeforeProcessStatusCheck: => Unit,
                                                executeAfterProcessStatusCheck: => Unit): Unit = {
    @volatile var process: Process = null
    val processExitCodeFuture = Future {
      process = Runtime.getRuntime.exec(shellScriptArgs,
        shellScriptEnvs)
      logger.info(s"Started runtime process with pid: ${process.pid()}")
      try {
        StreamUtils.copy(process.getInputStream, System.out)
        StreamUtils.copy(process.getErrorStream, System.err)
      } catch {
        case _: IOException => // ignore Stream closed
      }
      process.onExit().thenApply[Unit](_ => logger.info(s"Runtime process exited with ${process.exitValue()} exit code"))
      process.waitFor()
      process.exitValue()
    }

    try {
      executeBeforeProcessStatusCheck
      checkIfFailedInstantly(processExitCodeFuture)
      executeAfterProcessStatusCheck
    } catch {
      case NonFatal(ex) =>
        if (process != null && process.isAlive) {
          logger.info("Exception during test execution. Runtime process is still alive - dumping threads")
          // thread dump
          Runtime.getRuntime.exec(s"kill -3 ${process.pid()}")
          // wait a while to make sure that stack trace is presented in logs
          Thread.sleep(3000)
        }
        throw ex
    } finally {
      if (process != null) {
        process.destroy()
      }
    }

    processExitCodeFuture.futureValue shouldEqual 143 // success exit code TODO: shouldn't be just 0?
  }

  private def checkIfFailedInstantly(future: Future[Int]): Unit = {
    future.value match {
      case Some(tryValue) =>
        // If completed with failure instantly, fail to not shadow true failure by consume timeout
        tryValue.failed.toOption shouldBe empty
      case None =>
        // If not completed instantly but eventually completed with failure, we at least print error on console
        future.failed.foreach { ex =>
          ex.printStackTrace()
        }
    }
  }

  protected def shellScriptPath: Path = {
    val targetItClassesDir = Path.of(getClass.getResource("/").toURI)
    val liteModuleDir = targetItClassesDir.getParent.getParent.getParent.getParent
    val stageDir = liteModuleDir.resolve("runtime/target/universal/stage")
    stageDir.resolve("bin/run.sh")
  }

}
