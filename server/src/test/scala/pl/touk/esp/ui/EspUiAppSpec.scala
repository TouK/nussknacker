package pl.touk.esp.ui

import java.io.File
import java.nio.file.{Files, Paths}

import org.apache.commons.io.FileUtils
import org.scalatest.{BeforeAndAfterEach, FlatSpec}
import pl.touk.esp.ui.util.AvailablePortFinder


class EspUiAppSpec extends FlatSpec with BeforeAndAfterEach {

  var processesDir: File = _

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    processesDir = Files.createTempDirectory("processesJsons").toFile
    val sampleProcessesDir = new File(getClass.getResource("/jsons").getFile)
    FileUtils.copyDirectory(sampleProcessesDir, processesDir)
  }

  it should "start app without errors" in {
    val port = AvailablePortFinder.findAvailablePort()
    val args = Array(port.toString, processesDir.getAbsolutePath)
    EspUiApp.main(args)
  }
}