package pl.touk.nussknacker.ui.process.repository

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionId
import pl.touk.nussknacker.engine.api.deployment.scheduler.model.{DeploymentWithRuntimeParams, RuntimeParams}
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.test.base.db.WithHsqlDbTesting
import pl.touk.nussknacker.test.base.it.WithClock
import pl.touk.nussknacker.test.utils.domain.TestFactory.{
  newFutureFetchingScenarioRepository,
  newWriteProcessRepository
}
import pl.touk.nussknacker.test.utils.scalas.DBIOActionValues
import pl.touk.nussknacker.ui.process.periodic.CronScheduleProperty
import pl.touk.nussknacker.ui.process.periodic.model.{PeriodicProcess, ScheduleName}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction
import pl.touk.nussknacker.ui.security.api.AdminUser

import java.time.{Clock, LocalDateTime}
import java.util.UUID

/**
 * This repository differentiates PostgreSQL and HSQL databases, this test class exists to ensure that HSQL code path
 * functions correctly.
 */
class SlickPeriodicProcessesRepositoryHsqlSpec
    extends AnyFlatSpec
    with Matchers
    with OptionValues
    with PatientScalaFutures
    with WithHsqlDbTesting
    with WithClock
    with DBIOActionValues
    with LazyLogging {

  import scala.concurrent.ExecutionContext.Implicits.global

  override protected def dbioRunner: DBIOActionRunner = new DBIOActionRunner(testDbRef)

  private val processingType = "testProcessingType"

  private val sampleScenario = CanonicalProcess(MetaData("sample", StreamMetaData()), Nil)

  private lazy val repository = new SlickPeriodicProcessesRepository(
    processingType,
    testDbRef.db,
    testDbRef.profile,
    Clock.systemDefaultZone(),
    newFutureFetchingScenarioRepository(testDbRef),
  )

  private def runAction[T](action: repository.Action[T]): T = repository.run(action).futureValue

  private def prepareScenario(processName: ProcessName): ProcessIdWithName = {
    val action = CreateProcessAction(
      processName = processName,
      category = "Category1",
      canonicalProcess = CanonicalProcess(MetaData(processName.value, StreamMetaData()), Nil),
      processingType = "streaming",
      isFragment = false,
    )
    val processId = newWriteProcessRepository(testDbRef, clock)
      .saveNewProcess(action)(AdminUser("artificialTestAdmin", "artificialTestAdmin"))
      .dbioActionValues
      .value
      .processId
    ProcessIdWithName(processId, processName)
  }

  private def createPeriodicProcess(scenario: ProcessIdWithName): PeriodicProcess =
    runAction(
      repository.create(
        DeploymentWithRuntimeParams(
          processId = scenario.id,
          processName = scenario.name,
          versionId = VersionId(1),
          runtimeParams = RuntimeParams(Map.empty),
        ),
        inputConfigDuringExecutionJson = "{}",
        canonicalProcess = sampleScenario,
        scheduleProperty = CronScheduleProperty("0 0 * * * ?"),
        processActionId = ProcessActionId(UUID.randomUUID()),
      )
    )

  private def schedule(periodicProcess: PeriodicProcess, scheduleName: ScheduleName, runAt: LocalDateTime): Unit =
    runAction(repository.schedule(periodicProcess.id, scheduleName, runAt, deployMaxRetries = 0)): Unit

  private val baseRunAt = LocalDateTime.of(2026, 1, 1, 0, 0)

  it should "limit deployments per schedule for active schedules" in {
    val scenario        = prepareScenario(ProcessName("active"))
    val periodicProcess = createPeriodicProcess(scenario)
    val scheduleName    = ScheduleName(Some("everyHour"))

    val runAts = (1 to 5).map(i => baseRunAt.plusHours(i.toLong))
    runAts.foreach(schedule(periodicProcess, scheduleName, _))

    val state = runAction(repository.getLatestDeploymentsForActiveSchedules(scenario.name, 2))

    val deployments = state.schedules.values.flatMap(_.latestDeployments).toList
    deployments should have size 2
    // the window function orders by runAt desc, so the two most recent ones win
    deployments.map(_.runAt).toSet shouldBe runAts.takeRight(2).toSet
  }

  it should "return latest deployments of each schedule separately" in {
    val scenario        = prepareScenario(ProcessName("multipleSchedules"))
    val periodicProcess = createPeriodicProcess(scenario)

    List("first", "second").foreach { name =>
      (1 to 3).foreach(i => schedule(periodicProcess, ScheduleName(Some(name)), baseRunAt.plusHours(i.toLong)))
    }

    val state = runAction(repository.getLatestDeploymentsForActiveSchedules(scenario.name, 1))

    state.schedules should have size 2
    state.schedules.values.foreach(_.latestDeployments should have size 1)
    state.schedules.keys.map(_.scheduleName.value).toSet shouldBe Set(Some("first"), Some("second"))
  }

  it should "limit deployments per schedule for latest inactive schedules" in {
    val scenario        = prepareScenario(ProcessName("inactive"))
    val periodicProcess = createPeriodicProcess(scenario)
    val scheduleName    = ScheduleName(Some("everyHour"))

    (1 to 4).foreach(i => schedule(periodicProcess, scheduleName, baseRunAt.plusHours(i.toLong)))
    runAction(repository.markInactive(periodicProcess.id))

    runAction(repository.getLatestDeploymentsForActiveSchedules(scenario.name, 2)).schedules shouldBe empty

    val state = runAction(
      repository.getLatestDeploymentsForLatestInactiveSchedules(scenario.name, inactiveProcessesMaxCount = 1, 2)
    )

    state.schedules.values.flatMap(_.latestDeployments).toList should have size 2
  }

  it should "group latest deployments by scenario name when fetching all at once" in {
    val scenarios = List("firstScenario", "secondScenario").map { name =>
      val scenario        = prepareScenario(ProcessName(name))
      val periodicProcess = createPeriodicProcess(scenario)
      (1 to 3).foreach(i => schedule(periodicProcess, ScheduleName(None), baseRunAt.plusHours(i.toLong)))
      scenario
    }

    val states = runAction(repository.getLatestDeploymentsForActiveSchedules(2))

    states.keySet shouldBe scenarios.map(_.name).toSet
    states.values.foreach(_.schedules.values.flatMap(_.latestDeployments).toList should have size 2)
  }

}
