package pl.touk.nussknacker.ui.process

import akka.actor.ActorSystem
import akka.testkit.TestProbe
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.Deploy
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.processResolving
import pl.touk.nussknacker.ui.api.helpers.{MockFetchingProcessRepository, TestFactory}
import pl.touk.nussknacker.ui.db.DbConfig
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion
import slick.jdbc.{HsqldbProfile, JdbcBackend}

import java.time.Duration

class DBProcessServiceSpec extends FlatSpec with Matchers with PatientScalaFutures {
  import org.scalatest.prop.TableDrivenPropertyChecks._
  import pl.touk.nussknacker.ui.api.helpers.TestCategories._
  import pl.touk.nussknacker.ui.api.helpers.TestProcessUtil._

  import scala.concurrent.ExecutionContext.Implicits.global

  //These users were created based on categoriesConfig at ui.conf
  private val adminUser = TestFactory.adminUser()
  private val categoriesUser = TestFactory.userWithCategoriesReadPermission(username = "categoriesUser", categories = catCategories)
  private val testUser = TestFactory.userWithCategoriesReadPermission(username = "categoriesUser", categories = testCategories)
  private val testReqRespUser = TestFactory.userWithCategoriesReadPermission(username = "testReqRespUser", categories = testCategories ++ reqResCategories)

  private val category1Process = createBasicProcess("category1Process", isSubprocess = false, isArchived = false, category = Category1, lastAction = Some(Deploy))
  private val category2ArchivedProcess = createBasicProcess("category2ArchivedProcess", isSubprocess = false, isArchived = true, category = Category2)
  private val testSubProcess = createBasicProcess("testSubProcess", isSubprocess = true, isArchived = false, category = TESTCAT)
  private val reqRespArchivedSubProcess = createBasicProcess("reqRespArchivedSubProcess", isSubprocess = true, isArchived = true, category = ReqRes)

  private val processes: List[ProcessWithoutJson] = List(
    category1Process, category2ArchivedProcess, testSubProcess, reqRespArchivedSubProcess,
  )

  private val processCategoryService = new ConfigProcessCategoryService(ConfigWithScalaVersion.config)
  private val newProcessPreparer = TestFactory.createNewProcessPreparer()

  private val dummyDbConfig: Config = ConfigFactory.parseString("""db {url: "jdbc:hsqldb:mem:none"}""".stripMargin)
  private val dummyDb: DbConfig = DbConfig(JdbcBackend.Database.forConfig("db", dummyDbConfig), HsqldbProfile)

  private val DefaultRequestTimeLimit = Duration.ofMinutes(1)

  it should "return processes for each user" in {
    val dummyWriteProcessRepository = TestFactory.newWriteProcessRepository(dummyDb)
    val dummyActionRepository = TestFactory.newActionProcessRepository(dummyDb)
    val dummyRepositoryManager = TestFactory.newDBRepositoryManager(dummyDb)
    val mockRepository = MockFetchingProcessRepository(processes)

    implicit val system: ActorSystem = ActorSystem("DummyDBProcessServiceSpec")
    val dummyManagerActor = TestProbe().ref

    val dBProcessService = new DBProcessService(
      dummyManagerActor, DefaultRequestTimeLimit, newProcessPreparer, processCategoryService, processResolving,
      dummyRepositoryManager, mockRepository, dummyActionRepository, dummyWriteProcessRepository
    )

    val testingData = Table(
      ("user", "expected"),
      (adminUser, processes),
      (categoriesUser, List(category1Process, category2ArchivedProcess)),
      (testUser, List(testSubProcess)),
      (testReqRespUser, List(testSubProcess, reqRespArchivedSubProcess)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessWithoutJson]) =>
      val result = dBProcessService.getUserProcesses[Unit](user).futureValue
      result shouldBe expected
    }
  }
}
