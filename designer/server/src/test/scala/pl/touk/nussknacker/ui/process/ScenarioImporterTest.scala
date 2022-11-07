package pl.touk.nussknacker.ui.process

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.mapProcessingTypeDataProvider
import pl.touk.nussknacker.ui.api.helpers.{TestFactory, TestProcessingTypes, WithHsqlDbTesting}
import pl.touk.nussknacker.ui.process.repository.{DBProcessRepository, RepositoryManager}
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion

import java.io.File
import java.time.{Duration, Instant}
import scala.concurrent.ExecutionContext.Implicits.global

class ScenarioImporterTest
  extends AnyFunSuite
  with BeforeAndAfterEach
  with BeforeAndAfterAll
  with WithHsqlDbTesting
  with PatientScalaFutures
  with Matchers {

  private val adminUser = TestFactory.adminUser()

  private val repositoryManager = RepositoryManager.createDbRepositoryManager(db)

  private val repository = TestFactory.newFetchingProcessRepository(db, Some(1))

  private val writingRepo = new DBProcessRepository(db, mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> 0)) {
    override protected def now: Instant = Instant.ofEpochMilli(0)
  }

  private val processCategoryService = new ConfigProcessCategoryService(ConfigWithScalaVersion.TestsConfig)

  private val service =  new DBProcessService(
    managerActor = TestFactory.newDummyManagerActor(),
    requestTimeLimit = Duration.ofMinutes(1),
    newProcessPreparer = TestFactory.createNewProcessPreparer(),
    processCategoryService = processCategoryService,
    processResolving = TestFactory.processResolving,
    repositoryManager = repositoryManager,
    fetchingProcessRepository = repository,
    processActionRepository = TestFactory.newDummyActionRepository(),
    processRepository = writingRepo,
    processValidation = TestFactory.processValidation)

  test("should ignore not existing directory") {
    new ScenarioImporter(repository, service, new File("/tmp/not_existing")).importScenarios().futureValue
    service.getProcesses[Unit](adminUser).futureValue shouldBe empty
  }

  test("should import scenarios") {
    val importResult = new ScenarioImporter(repository, service, new File(getClass.getResource("/scenarios").getFile)).importScenarios().futureValue
    importResult should have size 2
    val scenariosAfterImport = service.getProcesses[Unit](adminUser).futureValue
    scenariosAfterImport should have size 2
    scenariosAfterImport.map(s => (s.processCategory, s.name)) should contain allOf (
      ("Category1", "name-from-b-json"),
      ("Category2", "name-from-a-json"))
  }

}
