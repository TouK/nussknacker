package pl.touk.nussknacker.engine.lite.utils

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.TestSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.{ProcessUtils, VeryPatientScalaFutures}

import java.nio.file.Path

trait BaseNuRuntimeBinTestMixin extends VeryPatientScalaFutures with Matchers { self: TestSuite with LazyLogging =>

  protected val pekkoManagementEnvs: Array[String] = Array(
    // random management port to avoid clashing of ports
    "CONFIG_FORCE_pekko_management_http_port=0",
    // It looks like github-actions doesn't look binding to 0.0.0.0, was problems like: Bind failed for TCP channel on endpoint [/10.1.0.183:0]
    "CONFIG_FORCE_pekko_management_http_hostname=127.0.0.1"
  )

  protected def withProcessExecutedInBackground(
      shellScriptArgs: Array[String],
      shellScriptEnvs: Array[String],
      executeBeforeProcessStatusCheck: => Unit,
      executeAfterProcessStatusCheck: => Unit
  ): Unit = {
    val process               = Runtime.getRuntime.exec(shellScriptArgs, shellScriptEnvs)
    val processExitCodeFuture = ProcessUtils.attachLoggingAndReturnWaitingFuture(process)
    ProcessUtils.destroyProcessEventually(process) {
      executeBeforeProcessStatusCheck
      ProcessUtils.checkIfFailedInstantly(processExitCodeFuture)
      executeAfterProcessStatusCheck
    }
    ProcessUtils.checkIfSucceedEventually(processExitCodeFuture)
  }

  protected def shellScriptPath: Path = {
    val targetItClassesDir = Path.of(getClass.getResource("/").toURI)
    val liteModuleDir      = targetItClassesDir.getParent.getParent.getParent.getParent
    val stageDir           = liteModuleDir.resolve("runtime-app/target/universal/stage")
    stageDir.resolve("bin/run.sh")
  }

}
