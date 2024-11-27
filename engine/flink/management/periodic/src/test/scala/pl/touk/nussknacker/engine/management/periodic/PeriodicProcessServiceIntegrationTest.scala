package pl.touk.nussknacker.engine.management.periodic

import com.cronutils.builder.CronBuilder
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.field.expression.FieldExpressionFactory.{on, questionMark}
import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.LoneElement._
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.testcontainers.utility.DockerImageName
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.{
  DataFreshnessPolicy,
  ProcessActionId,
  ProcessingTypeActionServiceStub,
  ScenarioActivity,
  ScenarioId,
  ScenarioUser,
  ScenarioVersionId,
  ScheduledExecutionStatus,
  UserName
}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.management.periodic.PeriodicProcessService.PeriodicProcessStatus
import pl.touk.nussknacker.engine.management.periodic.db.{DbInitializer, SlickPeriodicProcessesRepository}
import pl.touk.nussknacker.engine.management.periodic.model._
import pl.touk.nussknacker.engine.management.periodic.service._
import pl.touk.nussknacker.test.PatientScalaFutures
import slick.jdbc
import slick.jdbc.{JdbcBackend, JdbcProfile}

import java.time._
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

//Integration test with both in-memory hsql and postgres from test containers
class PeriodicProcessServiceIntegrationTest
    extends AnyFlatSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with PatientScalaFutures
    with ForAllTestContainer
    with LazyLogging {

  override val container: PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse("postgres:11.2"))

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh

  private val processingType = "testProcessingType"

  private val processName = ProcessName("test")

  private val processIdWithName = ProcessIdWithName(ProcessId(1), processName)

  private val sampleProcess = CanonicalProcess(MetaData(processName.value, StreamMetaData()), Nil)

  private val startTime = Instant.parse("2021-04-06T13:18:00Z")

  // we truncate to millis, as HSQL stores with that precision...
  private def fixedClock(instant: Instant) =
    Clock.tick(Clock.fixed(instant, ZoneOffset.UTC), java.time.Duration.ofMillis(1))

  private def localTime(instant: Instant) = LocalDateTime.now(fixedClock(instant))

  private val cronEveryHour = CronScheduleProperty("0 0 * * * ?")

  def withFixture(
      deploymentRetryConfig: DeploymentRetryConfig = DeploymentRetryConfig(),
      executionConfig: PeriodicExecutionConfig = PeriodicExecutionConfig()
  )(testCode: Fixture => Any): Unit = {
    val postgresConfig = ConfigFactory.parseMap(
      Map(
        "user"     -> container.username,
        "password" -> container.password,
        "url"      -> container.jdbcUrl,
        "driver"   -> "org.postgresql.Driver",
        "schema"   -> UUID.randomUUID().toString
      ).asJava
    )

    val hsqlConfig = ConfigFactory.parseMap(
      Map(
        "url"      -> s"jdbc:hsqldb:mem:periodic-${UUID.randomUUID().toString};sql.syntax_ora=true",
        "user"     -> "SA",
        "password" -> ""
      ).asJava
    )

    def runTestCodeWithDbConfig(config: Config) = {
      val (db: jdbc.JdbcBackend.DatabaseDef, dbProfile: JdbcProfile) = DbInitializer.init(config)
      try {
        testCode(new Fixture(db, dbProfile, deploymentRetryConfig, executionConfig))
      } finally {
        db.close()
      }
    }
    logger.debug("Running test with hsql")
    runTestCodeWithDbConfig(hsqlConfig)
    logger.debug("Running test with postgres")
    runTestCodeWithDbConfig(postgresConfig)
  }

  class Fixture(
      db: JdbcBackend.DatabaseDef,
      dbProfile: JdbcProfile,
      deploymentRetryConfig: DeploymentRetryConfig,
      executionConfig: PeriodicExecutionConfig
  ) {
    val delegateDeploymentManagerStub = new DeploymentManagerStub
    val jarManagerStub                = new JarManagerStub
    val events                        = new ArrayBuffer[PeriodicProcessEvent]()
    var failListener                  = false

    def periodicProcessService(currentTime: Instant, processingType: String = processingType) =
      new PeriodicProcessService(
        delegateDeploymentManager = delegateDeploymentManagerStub,
        jarManager = jarManagerStub,
        scheduledProcessesRepository =
          new SlickPeriodicProcessesRepository(db, dbProfile, fixedClock(currentTime), processingType),
        periodicProcessListener = new PeriodicProcessListener {

          override def onPeriodicProcessEvent: PartialFunction[PeriodicProcessEvent, Unit] = {
            case k if failListener => throw new Exception(s"$k was ordered to fail")
            case k                 => events.append(k)
          }

        },
        additionalDeploymentDataProvider = DefaultAdditionalDeploymentDataProvider,
        deploymentRetryConfig = deploymentRetryConfig,
        executionConfig = executionConfig,
        processConfigEnricher = ProcessConfigEnricher.identity,
        clock = fixedClock(currentTime),
        new ProcessingTypeActionServiceStub,
        Map.empty
      )

  }

  it should "handle basic flow" in withFixture() { f =>
    val timeToTriggerCheck   = startTime.plus(2, ChronoUnit.HOURS)
    val expectedScheduleTime = startTime.plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS)
    val (every30MinutesProcessName, cronEvery30Minutes) =
      (ProcessName("every30Minutes"), CronScheduleProperty("0 30 * * * ?"))
    val (every4HoursProcessName, cronEvery4Hours) = (ProcessName("every4Hours"), CronScheduleProperty("0 0 */4 * * ?"))

    var currentTime = startTime

    def service                    = f.periodicProcessService(currentTime)
    def otherProcessingTypeService = f.periodicProcessService(currentTime, processingType = "other")
    val otherProcessName           = ProcessName("other")

    service
      .schedule(
        cronEveryHour,
        ProcessVersion.empty.copy(processName = processName),
        sampleProcess,
        randomProcessActionId
      )
      .futureValue
    service
      .schedule(
        cronEvery30Minutes,
        ProcessVersion.empty.copy(processName = every30MinutesProcessName),
        sampleProcess,
        randomProcessActionId
      )
      .futureValue
    service
      .schedule(
        cronEvery4Hours,
        ProcessVersion.empty.copy(processName = every4HoursProcessName),
        sampleProcess,
        randomProcessActionId
      )
      .futureValue
    otherProcessingTypeService
      .schedule(
        cronEveryHour,
        ProcessVersion.empty.copy(processName = otherProcessName),
        sampleProcess,
        randomProcessActionId
      )
      .futureValue

    val stateAfterSchedule = service.getLatestDeploymentsForActiveSchedules(processName).futureValue
    stateAfterSchedule should have size 1
    val afterSchedule = stateAfterSchedule.firstScheduleData

    afterSchedule.process.processVersion.processName shouldBe processName
    afterSchedule.latestDeployments.head.state shouldBe PeriodicProcessDeploymentState(
      None,
      None,
      PeriodicProcessDeploymentStatus.Scheduled
    )
    afterSchedule.latestDeployments.head.runAt shouldBe localTime(expectedScheduleTime)
    service.getLatestDeploymentsForActiveSchedules(otherProcessName).futureValue shouldBe empty

    currentTime = timeToTriggerCheck

    val allToDeploy = service.findToBeDeployed.futureValue
    allToDeploy.map(
      _.periodicProcess.processVersion.processName
    ) should contain only (processName, every30MinutesProcessName)
    val toDeploy = allToDeploy.find(_.periodicProcess.processVersion.processName == processName).value
    service.deploy(toDeploy).futureValue
    otherProcessingTypeService.deploy(otherProcessingTypeService.findToBeDeployed.futureValue.loneElement).futureValue

    val stateAfterDeploy = service.getLatestDeploymentsForActiveSchedules(processName).futureValue
    stateAfterDeploy should have size 1
    val afterDeploy           = stateAfterDeploy.firstScheduleData
    val afterDeployDeployment = afterDeploy.latestDeployments.head

    afterDeployDeployment.id shouldBe afterSchedule.latestDeployments.head.id
    afterDeployDeployment.state shouldBe PeriodicProcessDeploymentState(
      Some(LocalDateTime.now(fixedClock(timeToTriggerCheck))),
      None,
      PeriodicProcessDeploymentStatus.Deployed
    )
    afterDeployDeployment.runAt shouldBe localTime(expectedScheduleTime)

    f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.Finished, Some(afterDeployDeployment.id))
    service.handleFinished.futureValue

    val toDeployAfterFinish = service.findToBeDeployed.futureValue
    toDeployAfterFinish.map(_.periodicProcess.processVersion.processName) should contain only every30MinutesProcessName
    service.deactivate(processName).futureValue
    service.getLatestDeploymentsForActiveSchedules(processName).futureValue shouldBe empty
    val inactiveStates = service
      .getLatestDeploymentsForLatestInactiveSchedules(
        processName,
        inactiveProcessesMaxCount = 1,
        deploymentsPerScheduleMaxCount = 1
      )
      .futureValue
    inactiveStates should have size 1
    // TODO: we currently don't have Canceled status - to get full information about status someone have to check both state of PeriodicProcess (active/inactive)
    //       and state of deployment
    inactiveStates.firstScheduleData.latestDeployments.head.state.status shouldBe PeriodicProcessDeploymentStatus.Scheduled

    val activities    = service.getScenarioActivitiesSpecificToPeriodicProcess(processIdWithName).futureValue
    val firstActivity = activities.head.asInstanceOf[ScenarioActivity.PerformedScheduledExecution]
    activities shouldBe List(
      ScenarioActivity.PerformedScheduledExecution(
        scenarioId = ScenarioId(1),
        scenarioActivityId = firstActivity.scenarioActivityId,
        user = ScenarioUser(None, UserName("Nussknacker"), None, None),
        date = firstActivity.date,
        scenarioVersionId = Some(ScenarioVersionId(1)),
        dateFinished = firstActivity.dateFinished,
        scheduleName = "[default]",
        scheduledExecutionStatus = ScheduledExecutionStatus.Finished,
        createdAt = firstActivity.createdAt,
        retriesLeft = None,
        nextRetryAt = None
      ),
    )
  }

  it should "redeploy scenarios that failed on deploy" in withFixture(deploymentRetryConfig =
    DeploymentRetryConfig(deployMaxRetries = 1)
  ) { f =>
    val timeToTriggerCheck = startTime.plus(2, ChronoUnit.HOURS)
    var currentTime        = startTime
    f.jarManagerStub.deployWithJarFuture = Future.failed(new RuntimeException("Flink deploy error"))

    def service = f.periodicProcessService(currentTime)
    service
      .schedule(
        cronEveryHour,
        ProcessVersion.empty.copy(processName = processName),
        sampleProcess,
        randomProcessActionId
      )
      .futureValue

    currentTime = timeToTriggerCheck
    val toDeploy :: Nil = service.findToBeDeployed.futureValue.toList

    service.deploy(toDeploy).futureValue

    val toBeRetried :: Nil = service.findToBeDeployed.futureValue.toList
    toBeRetried.state.status shouldBe PeriodicProcessDeploymentStatus.RetryingDeploy
    toBeRetried.retriesLeft shouldBe 1
    toBeRetried.nextRetryAt.isDefined shouldBe true

    service.deploy(toBeRetried).futureValue
    service.findToBeDeployed.futureValue.toList shouldBe Nil

    val activities    = service.getScenarioActivitiesSpecificToPeriodicProcess(processIdWithName).futureValue
    val firstActivity = activities.head.asInstanceOf[ScenarioActivity.PerformedScheduledExecution]
    activities shouldBe List(
      ScenarioActivity.PerformedScheduledExecution(
        scenarioId = ScenarioId(1),
        scenarioActivityId = firstActivity.scenarioActivityId,
        user = ScenarioUser(None, UserName("Nussknacker"), None, None),
        date = firstActivity.date,
        scenarioVersionId = Some(ScenarioVersionId(1)),
        dateFinished = firstActivity.dateFinished,
        scheduleName = "[default]",
        scheduledExecutionStatus = ScheduledExecutionStatus.DeploymentFailed,
        createdAt = firstActivity.createdAt,
        retriesLeft = None,
        nextRetryAt = None
      ),
    )
  }

  it should "handle multiple schedules" in withFixture() { f =>
    val expectedScheduleTime = startTime.plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS)
    val timeToTrigger        = startTime.plus(2, ChronoUnit.HOURS)

    var currentTime = startTime

    def service = f.periodicProcessService(currentTime)

    val scheduleMinute5  = "scheduleMinute5"
    val scheduleMinute10 = "scheduleMinute10"
    service
      .schedule(
        MultipleScheduleProperty(
          Map(
            scheduleMinute5  -> CronScheduleProperty("0 5 * * * ?"),
            scheduleMinute10 -> CronScheduleProperty("0 10 * * * ?")
          )
        ),
        ProcessVersion.empty.copy(processName = processName),
        sampleProcess,
        randomProcessActionId
      )
      .futureValue

    service
      .schedule(
        MultipleScheduleProperty(
          Map(
            // Same names but scheduled earlier and later.
            scheduleMinute5  -> CronScheduleProperty("0 15 * * * ?"),
            scheduleMinute10 -> CronScheduleProperty("0 1 * * * ?")
          )
        ),
        ProcessVersion.empty.copy(processName = ProcessName("other")),
        sampleProcess,
        randomProcessActionId
      )
      .futureValue

    val stateAfterSchedule = service.getLatestDeploymentsForActiveSchedules(processName).futureValue
    stateAfterSchedule should have size 2
    stateAfterSchedule.latestDeploymentForSchedule(scheduleMinute5).runAt shouldBe localTime(
      expectedScheduleTime.plus(5, ChronoUnit.MINUTES)
    )
    stateAfterSchedule.latestDeploymentForSchedule(scheduleMinute10).runAt shouldBe localTime(
      expectedScheduleTime.plus(10, ChronoUnit.MINUTES)
    )

    currentTime = timeToTrigger

    val allToDeploy = service.findToBeDeployed.futureValue
    allToDeploy should have length 4
    val toDeploy = allToDeploy.filter(_.periodicProcess.processVersion.processName == processName)
    toDeploy should have length 2
    toDeploy.head.runAt shouldBe localTime(expectedScheduleTime.plus(5, ChronoUnit.MINUTES))
    toDeploy.head.scheduleName.value shouldBe Some(scheduleMinute5)
    toDeploy.last.runAt shouldBe localTime(expectedScheduleTime.plus(10, ChronoUnit.MINUTES))
    toDeploy.last.scheduleName.value shouldBe Some(scheduleMinute10)

    service.deactivate(processName).futureValue
    service.getLatestDeploymentsForActiveSchedules(processName).futureValue shouldBe empty

    val activities = service.getScenarioActivitiesSpecificToPeriodicProcess(processIdWithName).futureValue
    activities shouldBe empty
  }

  it should "wait until other schedule finishes, before deploying next schedule" in withFixture() { f =>
    val timeToTrigger = startTime.plus(2, ChronoUnit.HOURS)

    var currentTime = startTime

    def service = f.periodicProcessService(currentTime)

    val firstSchedule  = "schedule1"
    val secondSchedule = "schedule2"
    service
      .schedule(
        MultipleScheduleProperty(
          Map(
            firstSchedule  -> CronScheduleProperty("0 5 * * * ?"),
            secondSchedule -> CronScheduleProperty("0 5 * * * ?")
          )
        ),
        ProcessVersion.empty.copy(processName = processName),
        sampleProcess,
        randomProcessActionId
      )
      .futureValue

    currentTime = timeToTrigger

    val toDeploy = service.findToBeDeployed.futureValue
    toDeploy should have length 2

    val deployment = toDeploy.find(_.scheduleName.value.contains(firstSchedule)).value
    service.deploy(deployment)
    f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.Running, Some(deployment.id))

    val toDeployAfterDeploy = service.findToBeDeployed.futureValue
    toDeployAfterDeploy should have length 0

    f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.Finished, Some(deployment.id))
    service.handleFinished.futureValue

    val toDeployAfterFinish = service.findToBeDeployed.futureValue
    toDeployAfterFinish should have length 1
    toDeployAfterFinish.head.scheduleName.value.value shouldBe secondSchedule

    val firstActivity = eventually {
      val result = service.getScenarioActivitiesSpecificToPeriodicProcess(processIdWithName).futureValue
      result should not be empty
      result.head.asInstanceOf[ScenarioActivity.PerformedScheduledExecution]
    }
    firstActivity shouldBe
      ScenarioActivity.PerformedScheduledExecution(
        scenarioId = ScenarioId(1),
        scenarioActivityId = firstActivity.scenarioActivityId,
        user = ScenarioUser(None, UserName("Nussknacker"), None, None),
        date = firstActivity.date,
        scenarioVersionId = Some(ScenarioVersionId(1)),
        dateFinished = firstActivity.dateFinished,
        scheduleName = "schedule1",
        scheduledExecutionStatus = ScheduledExecutionStatus.Finished,
        createdAt = firstActivity.createdAt,
        retriesLeft = None,
        nextRetryAt = None
      )
  }

  it should "handle multiple one time schedules" in withFixture() { f =>
    var currentTime            = startTime
    def service                = f.periodicProcessService(currentTime)
    val timeToTriggerSchedule1 = startTime.plus(1, ChronoUnit.HOURS)
    val timeToTriggerSchedule2 = startTime.plus(2, ChronoUnit.HOURS)

    def mostImportantActiveDeployment = service
      .getStatusDetails(processName)
      .futureValue
      .value
      .status
      .asInstanceOf[PeriodicProcessStatus]
      .pickMostImportantActiveDeployment
      .value

    val schedule1 = "schedule1"
    val schedule2 = "schedule2"
    service
      .schedule(
        MultipleScheduleProperty(
          Map(
            schedule1 -> CronScheduleProperty(convertDateToCron(localTime(timeToTriggerSchedule1))),
            schedule2 -> CronScheduleProperty(convertDateToCron(localTime(timeToTriggerSchedule2)))
          )
        ),
        ProcessVersion.empty.copy(processName = processName),
        sampleProcess,
        randomProcessActionId
      )
      .futureValue

    val stateAfterSchedule = service.getLatestDeploymentsForActiveSchedules(processName).futureValue
    stateAfterSchedule should have size 2

    val latestDeploymentSchedule1 = mostImportantActiveDeployment
    latestDeploymentSchedule1.scheduleName.value.value shouldBe schedule1
    latestDeploymentSchedule1.runAt shouldBe localTime(timeToTriggerSchedule1)

    currentTime = timeToTriggerSchedule1
    val toDeployOnSchedule1 = service.findToBeDeployed.futureValue.loneElement
    toDeployOnSchedule1.scheduleName.value.value shouldBe schedule1
    service.deploy(toDeployOnSchedule1).futureValue

    val stateAfterSchedule1Deploy = service.getLatestDeploymentsForActiveSchedules(processName).futureValue
    stateAfterSchedule1Deploy
      .latestDeploymentForSchedule(schedule1)
      .state
      .status shouldBe PeriodicProcessDeploymentStatus.Deployed
    stateAfterSchedule1Deploy
      .latestDeploymentForSchedule(schedule2)
      .state
      .status shouldBe PeriodicProcessDeploymentStatus.Scheduled
    mostImportantActiveDeployment.scheduleName.value.value shouldBe schedule1

    service.handleFinished.futureValue
    val toDeployAfterFinishSchedule1 = service.findToBeDeployed.futureValue
    toDeployAfterFinishSchedule1 should have length 0

    val stateAfterSchedule1Finished = service.getLatestDeploymentsForActiveSchedules(processName).futureValue
    stateAfterSchedule1Finished
      .latestDeploymentForSchedule(schedule1)
      .state
      .status shouldBe PeriodicProcessDeploymentStatus.Finished
    stateAfterSchedule1Finished
      .latestDeploymentForSchedule(schedule2)
      .state
      .status shouldBe PeriodicProcessDeploymentStatus.Scheduled
    val latestDeploymentSchedule2 = mostImportantActiveDeployment
    latestDeploymentSchedule2.scheduleName.value.value shouldBe schedule2
    latestDeploymentSchedule2.runAt shouldBe localTime(timeToTriggerSchedule2)

    currentTime = timeToTriggerSchedule2
    val toDeployOnSchedule2 = service.findToBeDeployed.futureValue.loneElement
    toDeployOnSchedule2.scheduleName.value.value shouldBe schedule2
    service.deploy(toDeployOnSchedule2).futureValue

    val stateAfterSchedule2Deploy = service.getLatestDeploymentsForActiveSchedules(processName).futureValue
    stateAfterSchedule2Deploy
      .latestDeploymentForSchedule(schedule1)
      .state
      .status shouldBe PeriodicProcessDeploymentStatus.Finished
    stateAfterSchedule2Deploy
      .latestDeploymentForSchedule(schedule2)
      .state
      .status shouldBe PeriodicProcessDeploymentStatus.Deployed
    mostImportantActiveDeployment.scheduleName.value.value shouldBe schedule2

    service.handleFinished.futureValue
    service.getLatestDeploymentsForActiveSchedules(processName).futureValue shouldBe empty
    val inactiveStates = service
      .getLatestDeploymentsForLatestInactiveSchedules(
        processName,
        inactiveProcessesMaxCount = 1,
        deploymentsPerScheduleMaxCount = 1
      )
      .futureValue
    inactiveStates.latestDeploymentForSchedule(schedule1).state.status shouldBe PeriodicProcessDeploymentStatus.Finished
    inactiveStates.latestDeploymentForSchedule(schedule2).state.status shouldBe PeriodicProcessDeploymentStatus.Finished

    val activities     = service.getScenarioActivitiesSpecificToPeriodicProcess(processIdWithName).futureValue
    val firstActivity  = activities.head.asInstanceOf[ScenarioActivity.PerformedScheduledExecution]
    val secondActivity = activities(1).asInstanceOf[ScenarioActivity.PerformedScheduledExecution]
    activities shouldBe List(
      ScenarioActivity.PerformedScheduledExecution(
        scenarioId = ScenarioId(1),
        scenarioActivityId = firstActivity.scenarioActivityId,
        user = ScenarioUser(None, UserName("Nussknacker"), None, None),
        date = firstActivity.date,
        scenarioVersionId = Some(ScenarioVersionId(1)),
        dateFinished = firstActivity.dateFinished,
        scheduleName = "schedule1",
        scheduledExecutionStatus = ScheduledExecutionStatus.Finished,
        createdAt = firstActivity.createdAt,
        retriesLeft = None,
        nextRetryAt = None
      ),
      ScenarioActivity.PerformedScheduledExecution(
        scenarioId = ScenarioId(1),
        scenarioActivityId = secondActivity.scenarioActivityId,
        user = ScenarioUser(None, UserName("Nussknacker"), None, None),
        date = secondActivity.date,
        scenarioVersionId = Some(ScenarioVersionId(1)),
        dateFinished = secondActivity.dateFinished,
        scheduleName = "schedule2",
        scheduledExecutionStatus = ScheduledExecutionStatus.Finished,
        createdAt = secondActivity.createdAt,
        retriesLeft = None,
        nextRetryAt = None
      ),
    )
  }

  it should "handle failed event handler" in withFixture() { f =>
    val timeToTriggerCheck = startTime.plus(2, ChronoUnit.HOURS)

    var currentTime = startTime

    def service = f.periodicProcessService(currentTime)

    def tryWithFailedListener[T](action: () => Future[T]): T = {
      f.failListener = true
      intercept[TestFailedException](action().futureValue).getCause shouldBe a[PeriodicProcessException]
      f.failListener = false
      action().futureValue
    }

    tryWithFailedListener { () =>
      service.schedule(
        cronEveryHour,
        ProcessVersion.empty.copy(processName = processName),
        sampleProcess,
        randomProcessActionId
      )
    }

    currentTime = timeToTriggerCheck
    val toDeploy = service.findToBeDeployed.futureValue
    toDeploy should have length 1
    val deployment = toDeploy.head
    service.deploy(deployment).futureValue
    f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.Finished, Some(deployment.id))

    tryWithFailedListener { () =>
      service.deactivate(processName)
    }
  }

  it should "reschedule after failed if configured" in withFixture(executionConfig =
    PeriodicExecutionConfig(rescheduleOnFailure = true)
  ) { f =>
    val timeToTriggerCheck = startTime.plus(1, ChronoUnit.HOURS)
    var currentTime        = startTime

    f.jarManagerStub.deployWithJarFuture = Future.failed(new RuntimeException("Flink deploy error"))
    def service = f.periodicProcessService(currentTime)

    service
      .schedule(
        cronEveryHour,
        ProcessVersion.empty.copy(processName = processName),
        sampleProcess,
        randomProcessActionId
      )
      .futureValue
    currentTime = timeToTriggerCheck
    val toDeploy = service.findToBeDeployed.futureValue.toList

    val deployment = toDeploy.head
    service.deploy(deployment).futureValue

    f.delegateDeploymentManagerStub.setStateStatus(ProblemStateStatus.Failed, Some(deployment.id))

    // this one is cyclically called by RescheduleActor
    service.handleFinished.futureValue

    val stateAfterHandleFinished = service.getLatestDeploymentsForActiveSchedules(processName).futureValue
    stateAfterHandleFinished.latestDeploymentForSingleSchedule.state.status shouldBe PeriodicProcessDeploymentStatus.Scheduled

    val activities    = service.getScenarioActivitiesSpecificToPeriodicProcess(processIdWithName).futureValue
    val firstActivity = activities.head.asInstanceOf[ScenarioActivity.PerformedScheduledExecution]
    activities shouldBe List(
      ScenarioActivity.PerformedScheduledExecution(
        scenarioId = ScenarioId(1),
        scenarioActivityId = firstActivity.scenarioActivityId,
        user = ScenarioUser(None, UserName("Nussknacker"), None, None),
        date = firstActivity.date,
        scenarioVersionId = Some(ScenarioVersionId(1)),
        dateFinished = firstActivity.dateFinished,
        scheduleName = "[default]",
        scheduledExecutionStatus = ScheduledExecutionStatus.Failed,
        createdAt = firstActivity.createdAt,
        retriesLeft = None,
        nextRetryAt = None
      ),
    )
  }

  private def randomProcessActionId = ProcessActionId(UUID.randomUUID())

  private def convertDateToCron(date: LocalDateTime): String = {
    CronBuilder
      .cron(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ))
      .withYear(on(date.getYear))
      .withMonth(on(date.getMonth.getValue))
      .withDoM(on(date.getDayOfMonth))
      .withDoW(questionMark())
      .withHour(on(date.getHour))
      .withMinute(on(date.getMinute))
      .withSecond(on(date.getSecond))
      .instance()
      .asString()
  }

  private implicit class SchedulesStateExt(schedulesState: SchedulesState) {

    def firstScheduleData: ScheduleData = schedulesState.schedules.values.head

    def latestDeploymentForSingleSchedule: ScheduleDeploymentData = {
      schedulesState.schedules.find(_._1.scheduleName.value.isEmpty).value._2.latestDeployments.head
    }

    def latestDeploymentForSchedule(name: String): ScheduleDeploymentData = {
      schedulesState.schedules.find(_._1.scheduleName.value.contains(name)).value._2.latestDeployments.head
    }

  }

}
