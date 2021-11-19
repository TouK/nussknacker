package pl.touk.nussknacker.ui.api.helpers

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.restmodel.ProcessType
import pl.touk.nussknacker.restmodel.processdetails.ProcessShapeFetchStrategy
import pl.touk.nussknacker.ui.security.api.LoggedUser
import scala.util.Try

class MockFetchingProcessRepositorySpec extends FlatSpec with Matchers with ScalaFutures {

  import ProcessActionType._
  import TestProcessingTypes._
  import org.scalatest.prop.TableDrivenPropertyChecks._
  import TestProcessUtil._

  import scala.concurrent.ExecutionContext.Implicits.global

  private val categoryMarketing = "marketing"
  private val categoryFraud = "fraud"
  private val categoryFraudSecond = "fraudSecond"
  private val categorySecret = "secret"
  private val categoryTechnical= "technical"

  private val marketingProcess = createBasicProcess("marketingProcess", isSubprocess = false, isArchived = false, category = categoryMarketing, lastAction = Some(Deploy))
  private val marketingSubprocess = createBasicProcess("marketingSubprocess", isSubprocess = true, isArchived = false, category = categoryMarketing)
  private val marketingArchivedSubprocess = createBasicProcess("marketingArchivedSubprocess", isSubprocess = true, isArchived = true, category = categoryMarketing, lastAction = Some(Archive))
  private val marketingArchivedProcess = createBasicProcess("marketingArchivedProcess", isSubprocess = false, isArchived = true, category = categoryMarketing, lastAction = Some(Archive))
  private val marketingCustomProcess = createBasicProcess("marketingCustomProcess", isSubprocess = false, isArchived = false, category = categoryMarketing, processType = ProcessType.Custom, lastAction = Some(Cancel))

  private val fraudProcess = createBasicProcess("fraudProcess", isSubprocess = false, isArchived = false, category = categoryFraud, processingType = Fraud, lastAction = Some(Deploy))
  private val fraudArchivedProcess = createBasicProcess("fraudArchivedProcess", isSubprocess = false, isArchived = true, category = categoryFraudSecond, processingType = Fraud, lastAction = Some(Archive))
  private val fraudSubprocess = createBasicProcess("fraudSubprocess", isSubprocess = true, isArchived = false, category = categoryFraud, processingType = Fraud)
  private val fraudArchivedSubprocess = createBasicProcess("fraudArchivedSubprocess", isSubprocess = true, isArchived = true, category = categoryFraud, processingType = Fraud)

  private val fraudSecondProcess = createBasicProcess("fraudSecondProcess", isSubprocess = false, isArchived = false, category = categoryFraudSecond, processingType = Fraud, lastAction = Some(Cancel))
  private val fraudSecondSubprocess = createBasicProcess("fraudSecondSubprocess", isSubprocess = true, isArchived = false, category = categoryFraudSecond, processingType = Fraud)

  private val secretProcess = createBasicProcess("secretProcess", isSubprocess = false, isArchived = false, category = categorySecret)
  private val secretSubprocess = createBasicProcess("secretSubprocess", isSubprocess = true, isArchived = false, category = categorySecret)
  private val secretArchivedSubprocess = createBasicProcess("secretArchivedSubprocess", isSubprocess = true, isArchived = true, category = categorySecret, lastAction = Some(Archive))
  private val secretArchivedProcess = createBasicProcess("secretArchivedProcess", isSubprocess = false, isArchived = true, category = categorySecret, lastAction = Some(Archive))

  private val customProcess = createBasicProcess("customProcess", isSubprocess = false, isArchived = false, category = categoryTechnical, processType = ProcessType.Custom, lastAction = Some(Deploy))
  private val customArchivedProcess = createBasicProcess("customArchivedProcess", isSubprocess = false, isArchived = true, category = categoryTechnical, processType = ProcessType.Custom, lastAction = Some(Archive))

  private val processes: List[ProcessWithoutJson] = List(
    marketingProcess, marketingArchivedProcess, marketingSubprocess, marketingArchivedSubprocess, marketingCustomProcess,
    fraudProcess, fraudArchivedProcess, fraudSubprocess, fraudArchivedSubprocess,
    fraudSecondProcess, fraudSecondSubprocess,
    secretProcess, secretArchivedProcess, secretSubprocess, secretArchivedSubprocess,
    customProcess, customArchivedProcess
  )

  private val admin: LoggedUser = TestFactory.adminUser()
  private val marketingUser: LoggedUser = TestFactory.userWithCategoriesReadPermission(categories = List(categoryMarketing))
  private val fraudUser: LoggedUser = TestFactory.userWithCategoriesReadPermission(categories = List(categoryFraud, categoryFraudSecond))

  private val NoShape: ProcessShapeFetchStrategy[Unit] = ProcessShapeFetchStrategy.NotFetch
  private val mockRepository = MockFetchingProcessRepository(processes)

  it should "fetchProcesses for each user" in {
    val testingData = Table(
      ("user", "expected"),
      (admin, List(marketingProcess, fraudProcess, fraudSecondProcess, secretProcess)),
      (marketingUser, List(marketingProcess)),
      (fraudUser, List(fraudProcess, fraudSecondProcess)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessWithoutJson]) =>
      val result = mockRepository.fetchProcesses[Unit]()(NoShape, user, global).futureValue
      result shouldBe expected
    }
  }

  it should "fetchCustomProcesses for each user" in {
    val testingData = Table(
      ("user", "expected"),
      (admin, List(marketingCustomProcess, customProcess)),
      (marketingUser, List(marketingCustomProcess)),
      (fraudUser, List()),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessWithoutJson]) =>
      val result = mockRepository.fetchCustomProcesses[Unit]()(NoShape, user, global).futureValue
      result shouldBe expected
    }
  }

  it should "fetchProcessesDetails for each user" in {
    val testingData = Table(
      ("user", "expected"),
      (admin, List(marketingProcess, marketingCustomProcess, fraudProcess, fraudSecondProcess, secretProcess, customProcess)),
      (marketingUser, List(marketingProcess, marketingCustomProcess)),
      (fraudUser, List(fraudProcess, fraudSecondProcess)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessWithoutJson]) =>
      val result = mockRepository.fetchProcessesDetails[Unit]()(NoShape, user, global).futureValue
      result shouldBe expected
    }
  }

  it should "fetchDeployedProcessesDetails for each user" in {
    val testingData = Table(
      ("user", "expected"),
      (admin, List(marketingProcess, fraudProcess, customProcess)),
      (marketingUser, List(marketingProcess)),
      (fraudUser, List(fraudProcess)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessWithoutJson]) =>
      val result = mockRepository.fetchDeployedProcessesDetails[Unit]()(NoShape, user, global).futureValue
      result shouldBe expected
    }
  }

  it should "fetchProcessesDetails by names for each user" in {
    val testingData = Table(
      ("user", "expected"),
      (admin, List(marketingProcess, marketingCustomProcess, fraudProcess, fraudSecondProcess, secretProcess, customProcess)),
      (marketingUser, List(marketingProcess, marketingCustomProcess)),
      (fraudUser, List(fraudProcess, fraudSecondProcess)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessWithoutJson]) =>
      val names = processes.map(_.idWithName.name)
      val result = mockRepository.fetchProcessesDetails[Unit](names)(NoShape, user, global).futureValue
      result shouldBe expected
    }
  }

  it should "fetchSubProcessesDetails for each user" in {
    val testingData = Table(
      ("user", "expected"),
      (admin, List(marketingSubprocess, fraudSubprocess, fraudSecondSubprocess, secretSubprocess)),
      (marketingUser, List(marketingSubprocess)),
      (fraudUser, List(fraudSubprocess, fraudSecondSubprocess)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessWithoutJson]) =>
      val result = mockRepository.fetchSubProcessesDetails[Unit]()(NoShape, user, global).futureValue
      result shouldBe expected
    }
  }

  it should "fetchAllProcessesDetails for each user" in {
    val testingData = Table(
      ("user", "expected"),
      (admin, List(marketingProcess, marketingSubprocess, marketingCustomProcess, fraudProcess, fraudSubprocess, fraudSecondProcess, fraudSecondSubprocess, secretProcess, secretSubprocess, customProcess)),
      (marketingUser, List(marketingProcess, marketingSubprocess, marketingCustomProcess)),
      (fraudUser, List(fraudProcess, fraudSubprocess, fraudSecondProcess, fraudSecondSubprocess)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessWithoutJson]) =>
      val result = mockRepository.fetchAllProcessesDetails[Unit]()(NoShape, user, global).futureValue
      result shouldBe expected
    }
  }

  it should "fetchArchivedProcesses for each user" in {
    val testingData = Table(
      ("user", "expected"),
      (admin, List(marketingArchivedProcess, marketingArchivedSubprocess, fraudArchivedProcess, fraudArchivedSubprocess, secretArchivedProcess, secretArchivedSubprocess, customArchivedProcess)),
      (marketingUser, List(marketingArchivedProcess, marketingArchivedSubprocess)),
      (fraudUser, List(fraudArchivedProcess, fraudArchivedSubprocess)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessWithoutJson]) =>
      val result = mockRepository.fetchArchivedProcesses[Unit]()(NoShape, user, global).futureValue
      result shouldBe expected
    }
  }

  it should "fetchLatestProcessDetailsForProcessId for each user" in {
    val testingData = Table(
      ("user", "ProcessWithoutNodes", "expected"),
      (admin, secretSubprocess, Some(secretSubprocess)),
      (marketingUser, marketingProcess, Some(marketingProcess)),
      (marketingUser, marketingArchivedProcess, Some(marketingArchivedProcess)),
      (marketingUser, marketingArchivedSubprocess, Some(marketingArchivedSubprocess)),
      (fraudUser, marketingProcess, None),
    )

    forAll(testingData) { (user: LoggedUser, process: ProcessWithoutJson, expected: Option[ProcessWithoutJson]) =>
      val result = mockRepository.fetchLatestProcessDetailsForProcessId[Unit](process.processId)(NoShape, user, global).futureValue
      result shouldBe expected
    }
  }

  it should "fetchProcessDetailsForId for each user" in {
    val testingData = Table(
      ("user", "processId", "versionId", "expected"),
      (admin, secretSubprocess.processId, secretSubprocess.processVersionId, Some(secretSubprocess)),
      (admin, secretSubprocess.processId, 666L, None),
      (marketingUser, marketingProcess.processId, marketingProcess.processVersionId, Some(marketingProcess)),
      (marketingUser, marketingProcess.processId, 666L, None),
      (marketingUser, marketingArchivedProcess.processId, marketingArchivedProcess.processVersionId, Some(marketingArchivedProcess)),
      (marketingUser, marketingArchivedProcess.processId, 666L, None),
      (marketingUser, marketingArchivedSubprocess.processId, marketingArchivedSubprocess.processVersionId, Some(marketingArchivedSubprocess)),
      (marketingUser, marketingArchivedSubprocess.processId, 666L, None),
      (fraudUser, marketingProcess.processId, marketingProcess.processVersionId, None),
    )

    forAll(testingData) { (user: LoggedUser, processId: ProcessId, versionId: Long, expected: Option[ProcessWithoutJson]) =>
      val result = mockRepository.fetchProcessDetailsForId[Unit](processId, versionId)(NoShape, user, global).futureValue
      result shouldBe expected
    }
  }

  it should "return ProcessId for ProcessName" in {
    val data = processes.map(p => (p.idWithName.name, Some(p.processId))) ++ List((ProcessName("not-exist-name"), None))

    data.foreach { case (processName, processId) =>
      val result = mockRepository.fetchProcessId(processName).futureValue
      result shouldBe processId
    }
  }

  it should "return ProcessName for ProcessId" in {
    val data = processes.map(p => (p.processId, Some(p.idWithName.name))) ++ List((ProcessId(666), None))

    data.foreach { case (processId, processName) =>
      val result = mockRepository.fetchProcessName(processId).futureValue
      result shouldBe processName
    }
  }

  it should "return ProcessingType for ProcessId" in {
    val testingData = Table(
      ("user", "userProcesses"),
      (admin, processes),
      (marketingUser, List(marketingProcess, marketingArchivedProcess, marketingSubprocess, marketingArchivedSubprocess, marketingCustomProcess)),
      (fraudUser, List(fraudProcess, fraudSubprocess, fraudSecondProcess, fraudSecondSubprocess, fraudArchivedProcess, fraudArchivedSubprocess)),
    )

    forAll(testingData) { (user: LoggedUser, userProcesses: List[ProcessWithoutJson]) =>
      processes.foreach(process => {
        val result = mockRepository.fetchProcessingType(process.processId)(user, global)
        val processingType = Try(result.futureValue).toOption
        val expected = if (userProcesses.contains(process)) Some(process.processingType) else None
        processingType shouldBe expected
      })
    }
  }

  it should "fetchProcesses for each user by mixed FetchQuery" in {
    //given
    val processesQuery = FetchQuery(None, None, None, None, None)
    val processesCategoryQuery = FetchQuery(None, None, None, Some(Seq(categoryMarketing, categoryFraud, categoryFraudSecond)), None)
    val processesCategoryTypesQuery = FetchQuery(None, None, None, Some(Seq(categoryMarketing, categoryFraud, categoryFraudSecond)), Some(List(Streaming)))

    val allProcessesQuery = FetchQuery(Some(false), Some(false), None, None, None)
    val deployedProcessesQuery = FetchQuery(Some(false), Some(false), Some(true), None, None)
    val deployedProcessesCategoryQuery = FetchQuery(Some(false), Some(false), Some(true), Some(Seq(categoryMarketing, categoryFraud, categoryFraudSecond)), None)
    val deployedProcessesCategoryProcessingTypesQuery = FetchQuery(Some(false), Some(false), Some(true), Some(Seq(categoryMarketing, categoryFraud, categoryFraudSecond)), Some(List(Streaming)))

    val notDeployedProcessesQuery = FetchQuery(Some(false), Some(false), Some(false), None, None)
    val notDeployedProcessesCategoryQuery = FetchQuery(Some(false), Some(false), Some(false), Some(Seq(categoryMarketing, categoryFraud, categoryFraudSecond)), None)
    val notDeployedProcessesCategoryProcessingTypesQuery = FetchQuery(Some(false), Some(false), Some(false), Some(Seq(categoryMarketing, categoryFraud, categoryFraudSecond)), Some(List(Streaming)))

    val archivedQuery = FetchQuery(None, Some(true), None, None, None)
    val archivedProcessesQuery = FetchQuery(Some(false), Some(true), None, None, None)
    val archivedProcessesCategoryQuery = FetchQuery(Some(false), Some(true), None, Some(Seq(categoryMarketing, categoryFraud, categoryFraudSecond)), None)
    val archivedProcessesCategoryProcessingTypesQuery = FetchQuery(Some(false), Some(true), None, Some(Seq(categoryMarketing, categoryFraud, categoryFraudSecond)), Some(List(Streaming)))

    val subProcessesQuery = FetchQuery(Some(true), None, None, None, None)
    val allSubProcessesQuery = FetchQuery(Some(true), Some(false), None, None, None)
    val allSubProcessesCategoryQuery = FetchQuery(Some(true), Some(false), None, Some(Seq(categoryMarketing, categoryFraud, categoryFraudSecond)), None)
    val allSubProcessesCategoryTypesQuery = FetchQuery(Some(true), Some(false), None, Some(Seq(categoryMarketing, categoryFraud, categoryFraudSecond)), Some(List(Streaming)))

    val allArchivedSubProcessesQuery = FetchQuery(Some(true), Some(true), None, None, None)
    val allArchivedSubProcessesCategoryQuery = FetchQuery(Some(true), Some(true), None, Some(Seq(categoryMarketing, categoryFraud, categoryFraudSecond)), None)
    val allArchivedSubProcessesCategoryTypesQuery = FetchQuery(Some(true), Some(true), None, Some(Seq(categoryMarketing, categoryFraud, categoryFraudSecond)), Some(List(Streaming)))

    //when
    val testingData = Table(
      ("user", "query", "expected"),
      //admin user
      (admin, processesQuery, processes),
      (admin, processesCategoryQuery, List(marketingProcess, marketingArchivedProcess, marketingSubprocess, marketingArchivedSubprocess, marketingCustomProcess, fraudProcess, fraudArchivedProcess, fraudSubprocess, fraudArchivedSubprocess, fraudSecondProcess, fraudSecondSubprocess)),
      (admin, processesCategoryTypesQuery, List(marketingProcess, marketingArchivedProcess, marketingSubprocess, marketingArchivedSubprocess, marketingCustomProcess)),
      (admin, allProcessesQuery, List(marketingProcess, marketingCustomProcess, fraudProcess, fraudSecondProcess, secretProcess, customProcess)),
      (admin, deployedProcessesQuery, List(marketingProcess, fraudProcess, customProcess)),
      (admin, deployedProcessesCategoryQuery, List(marketingProcess, fraudProcess)),
      (admin, deployedProcessesCategoryProcessingTypesQuery, List(marketingProcess)),
      (admin, notDeployedProcessesQuery, List(marketingCustomProcess, fraudSecondProcess, secretProcess)),
      (admin, notDeployedProcessesCategoryQuery, List(marketingCustomProcess, fraudSecondProcess)),
      (admin, notDeployedProcessesCategoryProcessingTypesQuery, List(marketingCustomProcess)),
      (admin, archivedQuery, List(marketingArchivedProcess, marketingArchivedSubprocess, fraudArchivedProcess, fraudArchivedSubprocess, secretArchivedProcess, secretArchivedSubprocess, customArchivedProcess)),
      (admin, archivedProcessesQuery, List(marketingArchivedProcess, fraudArchivedProcess, secretArchivedProcess, customArchivedProcess)),
      (admin, archivedProcessesCategoryQuery, List(marketingArchivedProcess, fraudArchivedProcess)),
      (admin, archivedProcessesCategoryProcessingTypesQuery, List(marketingArchivedProcess)),
      (admin, subProcessesQuery, List(marketingSubprocess, marketingArchivedSubprocess, fraudSubprocess, fraudArchivedSubprocess, fraudSecondSubprocess, secretSubprocess, secretArchivedSubprocess)),
      (admin, allSubProcessesQuery, List(marketingSubprocess, fraudSubprocess, fraudSecondSubprocess, secretSubprocess)),
      (admin, allSubProcessesCategoryQuery, List(marketingSubprocess, fraudSubprocess, fraudSecondSubprocess)),
      (admin, allSubProcessesCategoryTypesQuery, List(marketingSubprocess)),
      (admin, allArchivedSubProcessesQuery, List(marketingArchivedSubprocess, fraudArchivedSubprocess, secretArchivedSubprocess)),
      (admin, allArchivedSubProcessesCategoryQuery, List(marketingArchivedSubprocess, fraudArchivedSubprocess)),
      (admin, allArchivedSubProcessesCategoryTypesQuery, List(marketingArchivedSubprocess)),

      //marketing user
      (marketingUser, processesQuery, List(marketingProcess, marketingArchivedProcess, marketingSubprocess, marketingArchivedSubprocess, marketingCustomProcess)),
      (marketingUser, processesCategoryQuery, List(marketingProcess, marketingArchivedProcess, marketingSubprocess, marketingArchivedSubprocess, marketingCustomProcess)),
      (marketingUser, processesCategoryTypesQuery, List(marketingProcess, marketingArchivedProcess, marketingSubprocess, marketingArchivedSubprocess, marketingCustomProcess)),
      (marketingUser, allProcessesQuery, List(marketingProcess, marketingCustomProcess)),
      (marketingUser, deployedProcessesQuery, List(marketingProcess)),
      (marketingUser, deployedProcessesCategoryQuery, List(marketingProcess)),
      (marketingUser, deployedProcessesCategoryProcessingTypesQuery, List(marketingProcess)),
      (marketingUser, notDeployedProcessesQuery, List(marketingCustomProcess)),
      (marketingUser, notDeployedProcessesCategoryQuery, List(marketingCustomProcess)),
      (marketingUser, notDeployedProcessesCategoryProcessingTypesQuery, List(marketingCustomProcess)),
      (marketingUser, archivedQuery, List(marketingArchivedProcess, marketingArchivedSubprocess)),
      (marketingUser, archivedProcessesQuery, List(marketingArchivedProcess)),
      (marketingUser, archivedProcessesCategoryQuery, List(marketingArchivedProcess)),
      (marketingUser, archivedProcessesCategoryProcessingTypesQuery, List(marketingArchivedProcess)),
      (marketingUser, subProcessesQuery, List(marketingSubprocess, marketingArchivedSubprocess)),
      (marketingUser, allSubProcessesQuery, List(marketingSubprocess)),
      (marketingUser, allSubProcessesCategoryQuery, List(marketingSubprocess)),
      (marketingUser, allSubProcessesCategoryTypesQuery, List(marketingSubprocess)),
      (marketingUser, allArchivedSubProcessesQuery, List(marketingArchivedSubprocess)),
      (marketingUser, allArchivedSubProcessesCategoryQuery, List(marketingArchivedSubprocess)),
      (marketingUser, allArchivedSubProcessesCategoryTypesQuery, List(marketingArchivedSubprocess)),

      //fraud user
      (fraudUser, processesQuery, List(fraudProcess, fraudArchivedProcess, fraudSubprocess, fraudArchivedSubprocess, fraudSecondProcess, fraudSecondSubprocess)),
      (fraudUser, processesCategoryQuery, List(fraudProcess, fraudArchivedProcess, fraudSubprocess, fraudArchivedSubprocess, fraudSecondProcess, fraudSecondSubprocess)),
      (fraudUser, processesCategoryTypesQuery, List()),
      (fraudUser, allProcessesQuery, List(fraudProcess, fraudSecondProcess)),
      (fraudUser, deployedProcessesQuery, List(fraudProcess)),
      (fraudUser, deployedProcessesCategoryQuery, List(fraudProcess)),
      (fraudUser, deployedProcessesCategoryProcessingTypesQuery, List()),
      (fraudUser, notDeployedProcessesQuery, List(fraudSecondProcess)),
      (fraudUser, notDeployedProcessesCategoryQuery, List(fraudSecondProcess)),
      (fraudUser, notDeployedProcessesCategoryProcessingTypesQuery, List()),
      (fraudUser, archivedQuery, List(fraudArchivedProcess, fraudArchivedSubprocess)),
      (fraudUser, archivedProcessesQuery, List(fraudArchivedProcess)),
      (fraudUser, archivedProcessesCategoryQuery, List(fraudArchivedProcess)),
      (fraudUser, archivedProcessesCategoryProcessingTypesQuery, List()),
      (fraudUser, subProcessesQuery, List(fraudSubprocess, fraudArchivedSubprocess, fraudSecondSubprocess)),
      (fraudUser, allSubProcessesQuery, List(fraudSubprocess, fraudSecondSubprocess)),
      (fraudUser, allSubProcessesCategoryQuery, List(fraudSubprocess, fraudSecondSubprocess)),
      (fraudUser, allSubProcessesCategoryTypesQuery, List()),
      (fraudUser, allArchivedSubProcessesQuery, List(fraudArchivedSubprocess)),
      (fraudUser, allArchivedSubProcessesQuery, List(fraudArchivedSubprocess)),
      (fraudUser, allArchivedSubProcessesCategoryQuery, List(fraudArchivedSubprocess)),
      (fraudUser, allArchivedSubProcessesCategoryTypesQuery, List()),
    )

    forAll(testingData) { (user: LoggedUser, query: FetchQuery, expected: List[ProcessWithoutJson]) =>
      val result = mockRepository.fetchProcesses[Unit](query.isSubprocess, query.isArchived, query.isDeployed, query.categories, query.processingTypes)(NoShape, user, global).futureValue

      //then
      result shouldBe expected
    }
  }

  //TODO: Move it as Query Object and replace params at FetchingProcessRepository.fetchProcesses(isSubprocess, isArchived, isDeployed, categories, processingTypes)
  case class FetchQuery(isSubprocess: Option[Boolean], isArchived: Option[Boolean], isDeployed: Option[Boolean], categories: Option[Seq[String]], processingTypes: Option[Seq[String]])
}
