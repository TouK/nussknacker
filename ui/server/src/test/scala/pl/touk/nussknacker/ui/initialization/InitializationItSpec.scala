package pl.touk.nussknacker.ui.initialization

import java.io.File
import java.nio.file.{Files, Paths}
import java.util.concurrent.TimeUnit

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.TrueFileFilter
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, TestFactory}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessDetails
import pl.touk.nussknacker.ui.security.{LoggedUser, Permission}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class InitializationItSpec extends FlatSpec with ScalatestRouteTest with Matchers with ScalaFutures with BeforeAndAfterEach with EspItTest with Eventually {

  val toukuser = LoggedUser("TouK", "", List(Permission.Write, Permission.Admin), List())
  var processesDir: File = _

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    processesDir = Files.createTempDirectory("processesJsons").toFile
    val sampleProcessesDir = new File(getClass.getResource("/jsons").getFile)
    FileUtils.copyDirectory(sampleProcessesDir, processesDir)
    Files.deleteIfExists(Paths.get(processesDir.getAbsolutePath + "/standaloneProcesses/StandaloneCategory1/RequestResponseTest1.json")) //fixme make standalone mode testing easier
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    processesDir.delete()
  }

  it should "save json processes and delete files afterwards" in {
    val processesFromFilesCount = listFilesFromDir(processesDir).size
    processesFromFilesCount should be > 1
    Await.result(getAllProcesses, Duration.apply(1, TimeUnit.SECONDS)) should have size 0

    Initialization.init(Map(), processRepository, processActivityRepository,
      db, "test", isDevelopmentMode = false, initialProcessDirectory = processesDir)

    eventually {
      listFilesFromDir(processesDir) should have size 0
      Await.result(getAllProcesses, Duration.apply(1, TimeUnit.SECONDS)) should have size processesFromFilesCount
    }
  }

  private def getAllProcesses: Future[List[ProcessDetails]] = {
    processRepository.fetchProcessesDetails()(toukuser)
  }

  private def listFilesFromDir(dir: File) = {
    FileUtils.listFiles(dir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE).toArray.toList
  }
}
