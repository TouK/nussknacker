package pl.touk.nussknacker.ui.api.helpers

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType._
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.processdetails.{ProcessDetails, ProcessShapeFetchStrategy}
import pl.touk.nussknacker.ui.api.helpers.TestProcessUtil._
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes._
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository.FetchProcessesDetailsQuery
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.util
import scala.util.Try

class MockFetchingProcessRepositorySpec extends AnyFlatSpec with Matchers with ScalaFutures {

  import org.scalatest.prop.TableDrivenPropertyChecks._

  import scala.concurrent.ExecutionContext.Implicits.global

  private val categoryMarketing = "marketing"
  private val categoryFraud = "fraud"
  private val categoryFraudSecond = "fraudSecond"
  private val categorySecret = "secret"

  private val json = ProcessTestData.sampleDisplayableProcess
  private val subJson = ProcessConverter.toDisplayable(ProcessTestData.sampleSubprocess, Streaming)

  private val someVersion = VersionId(666L)

  private val marketingProcess = createBasicProcess("marketingProcess", category = categoryMarketing, lastAction = Some(Deploy), json = Some(json))
  private val marketingSubprocess = createSubProcess("marketingSubprocess", category = categoryMarketing, json = Some(subJson))
  private val marketingArchivedSubprocess = createSubProcess("marketingArchivedSubprocess", isArchived = true, category = categoryMarketing, lastAction = Some(Archive))
  private val marketingArchivedProcess = createBasicProcess("marketingArchivedProcess", isArchived = true, category = categoryMarketing, lastAction = Some(Archive))

  private val fraudProcess = createBasicProcess("fraudProcess", category = categoryFraud, processingType = Fraud, lastAction = Some(Deploy))
  private val fraudArchivedProcess = createBasicProcess("fraudArchivedProcess", isArchived = true, category = categoryFraudSecond, processingType = Fraud, lastAction = Some(Archive), json = Some(json))
  private val fraudSubprocess = createSubProcess("fraudSubprocess", category = categoryFraud, processingType = Fraud, json = Some(json))
  private val fraudArchivedSubprocess = createSubProcess("fraudArchivedSubprocess", isArchived = true, category = categoryFraud, processingType = Fraud, json = Some(subJson))

  private val fraudSecondProcess = createBasicProcess("fraudSecondProcess", category = categoryFraudSecond, processingType = Fraud, lastAction = Some(Cancel), json = Some(json))
  private val fraudSecondSubprocess = createSubProcess("fraudSecondSubprocess", category = categoryFraudSecond, processingType = Fraud)

  private val secretProcess = createBasicProcess("secretProcess", category = categorySecret)
  private val secretSubprocess = createSubProcess("secretSubprocess", category = categorySecret)
  private val secretArchivedSubprocess = createSubProcess("secretArchivedSubprocess", isArchived = true, category = categorySecret, lastAction = Some(Archive))
  private val secretArchivedProcess = createBasicProcess("secretArchivedProcess", isArchived = true, category = categorySecret, lastAction = Some(Archive), json = Some(json))

  private val processes: List[ProcessDetails] = List(
    marketingProcess, marketingArchivedProcess, marketingSubprocess, marketingArchivedSubprocess,
    fraudProcess, fraudArchivedProcess, fraudSubprocess, fraudArchivedSubprocess,
    fraudSecondProcess, fraudSecondSubprocess,
    secretProcess, secretArchivedProcess, secretSubprocess, secretArchivedSubprocess
  )

  private val admin: LoggedUser = TestFactory.adminUser()
  private val marketingUser: LoggedUser = TestFactory.userWithCategoriesReadPermission(categories = List(categoryMarketing))
  private val fraudUser: LoggedUser = TestFactory.userWithCategoriesReadPermission(categories = List(categoryFraud, categoryFraudSecond))

  private val DisplayableShape = ProcessShapeFetchStrategy.FetchDisplayable
  private val CanonicalShape = ProcessShapeFetchStrategy.FetchCanonical
  private val NoneShape = ProcessShapeFetchStrategy.NotFetch

  private val mockRepository = new MockFetchingProcessRepository(processes)

  it should "fetchProcessesDetails for each user" in {
    val testingData = Table(
      ("user", "expected"),
      (admin, List(marketingProcess, fraudProcess, fraudSecondProcess, secretProcess)),
      (marketingUser, List(marketingProcess)),
      (fraudUser, List(fraudProcess, fraudSecondProcess)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessDetails]) =>
      val result = mockRepository.fetchProcessesDetails()(DisplayableShape, user, global).futureValue
      result shouldBe expected
    }
  }

  it should "fetchDeployedProcessesDetails for each user" in {
    val testingData = Table(
      ("user", "expected"),
      (admin, List(marketingProcess, fraudProcess)),
      (marketingUser, List(marketingProcess)),
      (fraudUser, List(fraudProcess)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessDetails]) =>
      val result = mockRepository.fetchDeployedProcessesDetails()(DisplayableShape, user, global).futureValue
      result shouldBe expected
    }
  }

  it should "fetchProcessesDetails by names for each user" in {
    val testingData = Table(
      ("user", "expected"),
      (admin, List(marketingProcess, fraudProcess, fraudSecondProcess, secretProcess)),
      (marketingUser, List(marketingProcess)),
      (fraudUser, List(fraudProcess, fraudSecondProcess)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessDetails]) =>
      val names = processes.map(_.idWithName.name)
      val result = mockRepository.fetchProcessesDetails(FetchProcessesDetailsQuery(names = Some(names), isArchived = Some(false), isSubprocess = Some(false)))(DisplayableShape, user, global).futureValue
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

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessDetails]) =>
      val result = mockRepository.fetchSubProcessesDetails()(DisplayableShape, user, global).futureValue
      result shouldBe expected
    }
  }

  it should "fetchSubProcessesDetails with each processing shape strategy" in {
    val canonicalFraudSubprocess = fraudSubprocess.copy(json = ProcessConverter.fromDisplayable(fraudSubprocess.json))
    val subprocesses = List(marketingSubprocess, canonicalFraudSubprocess, fraudSecondSubprocess, secretSubprocess)
    val mixedMockRepository = new MockFetchingProcessRepository(subprocesses)

    val displayableSubProcesses = List(marketingSubprocess, fraudSubprocess, fraudSecondSubprocess, secretSubprocess)
    val canonicalSubProcesses = displayableSubProcesses.map(p => p.copy(json = ProcessConverter.fromDisplayable(p.json)))
    val noneSubProcesses = displayableSubProcesses.map(p => p.copy(json = ()))

    mixedMockRepository.fetchSubProcessesDetails()(DisplayableShape, admin, global).futureValue shouldBe displayableSubProcesses
    mixedMockRepository.fetchSubProcessesDetails()(CanonicalShape, admin, global).futureValue shouldBe canonicalSubProcesses
    mixedMockRepository.fetchSubProcessesDetails()(NoneShape, admin, global).futureValue shouldBe noneSubProcesses
  }

  it should "fetchAllProcessesDetails for each user" in {
    val testingData = Table(
      ("user", "expected"),
      (admin, List(marketingProcess, marketingSubprocess, fraudProcess, fraudSubprocess, fraudSecondProcess, fraudSecondSubprocess, secretProcess, secretSubprocess)),
      (marketingUser, List(marketingProcess, marketingSubprocess)),
      (fraudUser, List(fraudProcess, fraudSubprocess, fraudSecondProcess, fraudSecondSubprocess)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessDetails]) =>
      val result = mockRepository.fetchAllProcessesDetails()(DisplayableShape, user, global).futureValue
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

    forAll(testingData) { (user: LoggedUser, process: ProcessDetails, expected: Option[ProcessDetails]) =>
      val result = mockRepository.fetchLatestProcessDetailsForProcessId(process.processId)(DisplayableShape, user, global).futureValue
      result shouldBe expected
    }
  }

  it should "fetchProcessDetailsForId for each user" in {
    val testingData = Table(
      ("user", "processId", "versionId", "expected"),
      (admin, secretSubprocess.processId, secretSubprocess.processVersionId, Some(secretSubprocess)),
      (admin, secretSubprocess.processId, someVersion, None),
      (marketingUser, marketingProcess.processId, marketingProcess.processVersionId, Some(marketingProcess)),
      (marketingUser, marketingProcess.processId, someVersion, None),
      (marketingUser, marketingArchivedProcess.processId, marketingArchivedProcess.processVersionId, Some(marketingArchivedProcess)),
      (marketingUser, marketingArchivedProcess.processId, someVersion, None),
      (marketingUser, marketingArchivedSubprocess.processId, marketingArchivedSubprocess.processVersionId, Some(marketingArchivedSubprocess)),
      (marketingUser, marketingArchivedSubprocess.processId, someVersion, None),
      (fraudUser, marketingProcess.processId, marketingProcess.processVersionId, None),
    )

    forAll(testingData) { (user: LoggedUser, processId: ProcessId, versionId: VersionId, expected: Option[ProcessDetails]) =>
      val result = mockRepository.fetchProcessDetailsForId(processId, versionId)(DisplayableShape, user, global).futureValue
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
      (marketingUser, List(marketingProcess, marketingArchivedProcess, marketingSubprocess, marketingArchivedSubprocess)),
      (fraudUser, List(fraudProcess, fraudSubprocess, fraudSecondProcess, fraudSecondSubprocess, fraudArchivedProcess, fraudArchivedSubprocess)),
    )

    forAll(testingData) { (user: LoggedUser, userProcesses: List[ProcessDetails]) =>
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
    val allProcessesQuery = FetchProcessesDetailsQuery()
    val allProcessesCategoryQuery = allProcessesQuery.copy(categories = Some(Seq(categoryMarketing, categoryFraud, categoryFraudSecond)))
    val allProcessesCategoryTypesQuery = allProcessesCategoryQuery.copy(processingTypes = Some(List(Streaming)))

    val processesQuery = FetchProcessesDetailsQuery(isSubprocess = Some(false), isArchived = Some(false))
    val deployedProcessesQuery = processesQuery.copy(isDeployed = Some(true))
    val deployedProcessesCategoryQuery = deployedProcessesQuery.copy(categories = Some(Seq(categoryMarketing, categoryFraud, categoryFraudSecond)))
    val deployedProcessesCategoryProcessingTypesQuery = deployedProcessesCategoryQuery.copy(processingTypes = Some(List(Streaming)))

    val notDeployedProcessesQuery = processesQuery.copy(isDeployed = Some(false))
    val notDeployedProcessesCategoryQuery = notDeployedProcessesQuery.copy(categories = Some(Seq(categoryMarketing, categoryFraud, categoryFraudSecond)))
    val notDeployedProcessesCategoryProcessingTypesQuery = notDeployedProcessesCategoryQuery.copy(processingTypes = Some(List(Streaming)))

    val archivedQuery = FetchProcessesDetailsQuery(isArchived = Some(true))
    val archivedProcessesQuery = archivedQuery.copy(isSubprocess = Some(false))
    val archivedProcessesCategoryQuery = archivedProcessesQuery.copy(categories = Some(Seq(categoryMarketing, categoryFraud, categoryFraudSecond)))
    val archivedProcessesCategoryProcessingTypesQuery = archivedProcessesCategoryQuery.copy(processingTypes = Some(List(Streaming)))

    val allSubProcessesQuery = FetchProcessesDetailsQuery(isSubprocess = Some(true))
    val subProcessesQuery = allSubProcessesQuery.copy(isArchived = Some(false))
    val subProcessesCategoryQuery = subProcessesQuery.copy(categories = Some(Seq(categoryMarketing, categoryFraud, categoryFraudSecond)))
    val subProcessesCategoryTypesQuery = subProcessesCategoryQuery.copy(processingTypes = Some(List(Streaming)))

    val archivedSubProcessesQuery = FetchProcessesDetailsQuery(isSubprocess = Some(true), isArchived = Some(true))
    val archivedSubProcessesCategoryQuery = archivedSubProcessesQuery.copy(categories = Some(Seq(categoryMarketing, categoryFraud, categoryFraudSecond)))
    val archivedSubProcessesCategoryTypesQuery = archivedSubProcessesCategoryQuery.copy(processingTypes = Some(List(Streaming)))

    //when
    val testingData = Table(
      ("user", "query", "expected"),
      //admin user
      (admin, allProcessesQuery, processes),
      (admin, allProcessesCategoryQuery, List(marketingProcess, marketingArchivedProcess, marketingSubprocess, marketingArchivedSubprocess, fraudProcess, fraudArchivedProcess, fraudSubprocess, fraudArchivedSubprocess, fraudSecondProcess, fraudSecondSubprocess)),
      (admin, allProcessesCategoryTypesQuery, List(marketingProcess, marketingArchivedProcess, marketingSubprocess, marketingArchivedSubprocess)),
      (admin, processesQuery, List(marketingProcess, fraudProcess, fraudSecondProcess, secretProcess)),
      (admin, deployedProcessesQuery, List(marketingProcess, fraudProcess)),
      (admin, deployedProcessesCategoryQuery, List(marketingProcess, fraudProcess)),
      (admin, deployedProcessesCategoryProcessingTypesQuery, List(marketingProcess)),
      (admin, notDeployedProcessesQuery, List(fraudSecondProcess, secretProcess)),
      (admin, notDeployedProcessesCategoryQuery, List(fraudSecondProcess)),
      (admin, notDeployedProcessesCategoryProcessingTypesQuery, List()),
      (admin, archivedQuery, List(marketingArchivedProcess, marketingArchivedSubprocess, fraudArchivedProcess, fraudArchivedSubprocess, secretArchivedProcess, secretArchivedSubprocess)),
      (admin, archivedProcessesQuery, List(marketingArchivedProcess, fraudArchivedProcess, secretArchivedProcess)),
      (admin, archivedProcessesCategoryQuery, List(marketingArchivedProcess, fraudArchivedProcess)),
      (admin, archivedProcessesCategoryProcessingTypesQuery, List(marketingArchivedProcess)),
      (admin, allSubProcessesQuery, List(marketingSubprocess, marketingArchivedSubprocess, fraudSubprocess, fraudArchivedSubprocess, fraudSecondSubprocess, secretSubprocess, secretArchivedSubprocess)),
      (admin, subProcessesQuery, List(marketingSubprocess, fraudSubprocess, fraudSecondSubprocess, secretSubprocess)),
      (admin, subProcessesCategoryQuery, List(marketingSubprocess, fraudSubprocess, fraudSecondSubprocess)),
      (admin, subProcessesCategoryTypesQuery, List(marketingSubprocess)),
      (admin, archivedSubProcessesQuery, List(marketingArchivedSubprocess, fraudArchivedSubprocess, secretArchivedSubprocess)),
      (admin, archivedSubProcessesCategoryQuery, List(marketingArchivedSubprocess, fraudArchivedSubprocess)),
      (admin, archivedSubProcessesCategoryTypesQuery, List(marketingArchivedSubprocess)),

      //marketing user
      (marketingUser, allProcessesQuery, List(marketingProcess, marketingArchivedProcess, marketingSubprocess, marketingArchivedSubprocess)),
      (marketingUser, allProcessesCategoryQuery, List(marketingProcess, marketingArchivedProcess, marketingSubprocess, marketingArchivedSubprocess)),
      (marketingUser, allProcessesCategoryTypesQuery, List(marketingProcess, marketingArchivedProcess, marketingSubprocess, marketingArchivedSubprocess)),
      (marketingUser, processesQuery, List(marketingProcess)),
      (marketingUser, deployedProcessesQuery, List(marketingProcess)),
      (marketingUser, deployedProcessesCategoryQuery, List(marketingProcess)),
      (marketingUser, deployedProcessesCategoryProcessingTypesQuery, List(marketingProcess)),
      (marketingUser, notDeployedProcessesQuery, List()),
      (marketingUser, notDeployedProcessesCategoryQuery, List()),
      (marketingUser, notDeployedProcessesCategoryProcessingTypesQuery, List()),
      (marketingUser, archivedQuery, List(marketingArchivedProcess, marketingArchivedSubprocess)),
      (marketingUser, archivedProcessesQuery, List(marketingArchivedProcess)),
      (marketingUser, archivedProcessesCategoryQuery, List(marketingArchivedProcess)),
      (marketingUser, archivedProcessesCategoryProcessingTypesQuery, List(marketingArchivedProcess)),
      (marketingUser, allSubProcessesQuery, List(marketingSubprocess, marketingArchivedSubprocess)),
      (marketingUser, subProcessesQuery, List(marketingSubprocess)),
      (marketingUser, subProcessesCategoryQuery, List(marketingSubprocess)),
      (marketingUser, subProcessesCategoryTypesQuery, List(marketingSubprocess)),
      (marketingUser, archivedSubProcessesQuery, List(marketingArchivedSubprocess)),
      (marketingUser, archivedSubProcessesCategoryQuery, List(marketingArchivedSubprocess)),
      (marketingUser, archivedSubProcessesCategoryTypesQuery, List(marketingArchivedSubprocess)),

      //fraud user
      (fraudUser, allProcessesQuery, List(fraudProcess, fraudArchivedProcess, fraudSubprocess, fraudArchivedSubprocess, fraudSecondProcess, fraudSecondSubprocess)),
      (fraudUser, allProcessesCategoryQuery, List(fraudProcess, fraudArchivedProcess, fraudSubprocess, fraudArchivedSubprocess, fraudSecondProcess, fraudSecondSubprocess)),
      (fraudUser, allProcessesCategoryTypesQuery, List()),
      (fraudUser, processesQuery, List(fraudProcess, fraudSecondProcess)),
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
      (fraudUser, allSubProcessesQuery, List(fraudSubprocess, fraudArchivedSubprocess, fraudSecondSubprocess)),
      (fraudUser, subProcessesQuery, List(fraudSubprocess, fraudSecondSubprocess)),
      (fraudUser, subProcessesCategoryQuery, List(fraudSubprocess, fraudSecondSubprocess)),
      (fraudUser, subProcessesCategoryTypesQuery, List()),
      (fraudUser, archivedSubProcessesQuery, List(fraudArchivedSubprocess)),
      (fraudUser, archivedSubProcessesQuery, List(fraudArchivedSubprocess)),
      (fraudUser, archivedSubProcessesCategoryQuery, List(fraudArchivedSubprocess)),
      (fraudUser, archivedSubProcessesCategoryTypesQuery, List()),
    )

    forAll(testingData) { (user: LoggedUser, query: FetchProcessesDetailsQuery, expected: List[ProcessDetails]) =>
      val result = mockRepository.fetchProcessesDetails(query)(DisplayableShape, user, global).futureValue

      //then
      result shouldBe expected
    }
  }
}
