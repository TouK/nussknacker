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
import org.testcontainers.utility.DockerImageName
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.management.periodic.db.{DbInitializer, SlickPeriodicProcessesRepository}
import pl.touk.nussknacker.engine.management.periodic.model.{PeriodicProcessDeploymentState, PeriodicProcessDeploymentStatus}
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
class PeriodicProcessServiceIntegrationTest extends AnyFlatSpec
  with Matchers
  with OptionValues
  with ScalaFutures
  with PatientScalaFutures
  with ForAllTestContainer
  with LazyLogging {

  override val container: PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse("postgres:11.2"))

  import scala.concurrent.ExecutionContext.Implicits.global

  private val processingType = "testProcessingType"

  private val processName = ProcessName("test")

  private val sampleProcess = CanonicalProcess(MetaData(processName.value, StreamMetaData()), Nil)

  private val startTime = Instant.parse("2021-04-06T13:18:00Z")

  //we truncate to millis, as HSQL stores with that precision...
  private def fixedClock(instant: Instant) =
    Clock.tick(Clock.fixed(instant, ZoneOffset.UTC), java.time.Duration.ofMillis(1))

  private def localTime(instant: Instant) = LocalDateTime.now(fixedClock(instant))

  private val cronEveryHour = CronScheduleProperty("0 0 * * * ?")

  def withFixture(deploymentRetryConfig: DeploymentRetryConfig = DeploymentRetryConfig(),
                  executionConfig: PeriodicExecutionConfig = PeriodicExecutionConfig())
                 (testCode: Fixture => Any): Unit = {
    val postgresConfig = ConfigFactory.parseMap(Map(
      "user" -> container.username,
      "password" -> container.password,
      "url" -> container.jdbcUrl,
      "driver" -> "org.postgresql.Driver",
      "schema" -> UUID.randomUUID().toString
    ).asJava)

    val hsqlConfig = ConfigFactory.parseMap(Map(
      "url" -> s"jdbc:hsqldb:mem:periodic-${UUID.randomUUID().toString};sql.syntax_ora=true",
      "user" -> "SA",
      "password" -> ""
      ).asJava)

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

  class Fixture(db: JdbcBackend.DatabaseDef, dbProfile: JdbcProfile,
                deploymentRetryConfig: DeploymentRetryConfig,
                executionConfig: PeriodicExecutionConfig) {
    val delegateDeploymentManagerStub = new DeploymentManagerStub
    val jarManagerStub = new JarManagerStub
    val events = new ArrayBuffer[PeriodicProcessEvent]()
    var failListener = false
    def periodicProcessService(currentTime: Instant, processingType: String = processingType) = new PeriodicProcessService(
      delegateDeploymentManager = delegateDeploymentManagerStub,
      jarManager = jarManagerStub,
      scheduledProcessesRepository =  new SlickPeriodicProcessesRepository(db, dbProfile, fixedClock(currentTime), processingType),
      periodicProcessListener = new PeriodicProcessListener {
        override def onPeriodicProcessEvent: PartialFunction[PeriodicProcessEvent, Unit] = {
          case k if failListener => throw new Exception(s"$k was ordered to fail")
          case k => events.append(k)
        }
      },
      additionalDeploymentDataProvider = DefaultAdditionalDeploymentDataProvider,
      deploymentRetryConfig = deploymentRetryConfig,
      executionConfig = executionConfig,
      processConfigEnricher = ProcessConfigEnricher.identity,
      clock = fixedClock(currentTime)
    )
  }

  it should "handle basic flow" in withFixture() { f =>
    val timeToTriggerCheck = startTime.plus(2, ChronoUnit.HOURS)
    val expectedScheduleTime = startTime.plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS)
    val (every30MinutesProcessName, cronEvery30Minutes) = (ProcessName("every30Minutes"), CronScheduleProperty("0 30 * * * ?"))
    val (every4HoursProcessName, cronEvery4Hours) = (ProcessName("every4Hours"), CronScheduleProperty("0 0 */4 * * ?"))

    var currentTime = startTime

    def service = f.periodicProcessService(currentTime)
    def otherProcessingTypeService = f.periodicProcessService(currentTime, processingType = "other")
    val otherProcessName = ProcessName("other")

    service.schedule(cronEveryHour, ProcessVersion.empty.copy(processName = processName), sampleProcess).futureValue
    service.schedule(cronEvery30Minutes, ProcessVersion.empty.copy(processName = every30MinutesProcessName), sampleProcess).futureValue
    service.schedule(cronEvery4Hours, ProcessVersion.empty.copy(processName = every4HoursProcessName), sampleProcess).futureValue
    otherProcessingTypeService.schedule(cronEveryHour, ProcessVersion.empty.copy(processName = otherProcessName), sampleProcess).futureValue

    val processScheduled = service.getLatestDeploymentForActiveSchedulesOld(processName).futureValue.value

    processScheduled.periodicProcess.processVersion.processName shouldBe processName
    processScheduled.state shouldBe PeriodicProcessDeploymentState(None, None, PeriodicProcessDeploymentStatus.Scheduled)
    processScheduled.runAt shouldBe localTime(expectedScheduleTime)
    service.getLatestDeploymentForActiveSchedulesOld(otherProcessName).futureValue shouldBe Symbol("empty")

    currentTime = timeToTriggerCheck
    
    val allToDeploy = service.findToBeDeployed.futureValue
    allToDeploy.map(_.periodicProcess.processVersion.processName) should contain only (processName, every30MinutesProcessName)
    val toDeploy = allToDeploy.find(_.periodicProcess.processVersion.processName == processName).value
    service.deploy(toDeploy).futureValue
    otherProcessingTypeService.deploy(otherProcessingTypeService.findToBeDeployed.futureValue.loneElement).futureValue

    val processDeployed = service.getLatestDeploymentForActiveSchedulesOld(processName).futureValue.value
    processDeployed.id shouldBe processScheduled.id
    processDeployed.state shouldBe PeriodicProcessDeploymentState(Some(LocalDateTime.now(fixedClock(timeToTriggerCheck))), None, PeriodicProcessDeploymentStatus.Deployed)
    processDeployed.runAt shouldBe localTime(expectedScheduleTime)

    f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.Finished, Some(processDeployed.id))
    service.handleFinished.futureValue

    val toDeployAfterFinish = service.findToBeDeployed.futureValue
    toDeployAfterFinish.map(_.periodicProcess.processVersion.processName) should contain only every30MinutesProcessName
    service.deactivate(processName).futureValue
    service.getLatestDeploymentForActiveSchedulesOld(processName).futureValue shouldBe None
  }

  it should "redeploy scenarios that failed on deploy" in withFixture(deploymentRetryConfig = DeploymentRetryConfig(deployMaxRetries = 1)) { f =>
    val timeToTriggerCheck = startTime.plus(2, ChronoUnit.HOURS)
    var currentTime = startTime
    f.jarManagerStub.deployWithJarFuture = Future.failed(new RuntimeException("Flink deploy error"))

    def service = f.periodicProcessService(currentTime)
    service.schedule(cronEveryHour, ProcessVersion.empty.copy(processName = processName), sampleProcess).futureValue

    currentTime = timeToTriggerCheck
    val toDeploy :: Nil = service.findToBeDeployed.futureValue.toList

    service.deploy(toDeploy).futureValue

    val toBeRetried :: Nil = service.findToBeDeployed.futureValue.toList
    toBeRetried.state.status shouldBe PeriodicProcessDeploymentStatus.RetryingDeploy
    toBeRetried.retriesLeft shouldBe 1
    toBeRetried.nextRetryAt.isDefined shouldBe true

    service.deploy(toBeRetried).futureValue
    service.findToBeDeployed.futureValue.toList shouldBe Nil
  }

  it should "handle multiple schedules" in withFixture() { f =>
    val expectedScheduleTime = startTime.plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS)
    val timeToTrigger = startTime.plus(2, ChronoUnit.HOURS)

    var currentTime = startTime

    def service = f.periodicProcessService(currentTime)


    service.schedule(MultipleScheduleProperty(Map(
      "scheduleMinute5" -> CronScheduleProperty("0 5 * * * ?"),
      "scheduleMinute10" -> CronScheduleProperty("0 10 * * * ?"))),
      ProcessVersion.empty.copy(processName = processName), sampleProcess).futureValue

    service.schedule(MultipleScheduleProperty(Map(
      // Same names but scheduled earlier and later.
      "scheduleMinute5" -> CronScheduleProperty("0 15 * * * ?"),
      "scheduleMinute10" -> CronScheduleProperty("0 1 * * * ?"))),
      ProcessVersion.empty.copy(processName = ProcessName("other")), sampleProcess).futureValue

    val processScheduled = service.getLatestDeploymentForActiveSchedulesOld(processName).futureValue.value

    processScheduled.periodicProcess.processVersion.processName shouldBe processName
    processScheduled.scheduleName shouldBe Some("scheduleMinute5")
    processScheduled.runAt shouldBe localTime(expectedScheduleTime.plus(5, ChronoUnit.MINUTES))

    currentTime = timeToTrigger

    val allToDeploy = service.findToBeDeployed.futureValue
    allToDeploy should have length 4
    val toDeploy = allToDeploy.filter(_.periodicProcess.processVersion.processName == processName)
    toDeploy should have length 2
    toDeploy.head.runAt shouldBe localTime(expectedScheduleTime.plus(5, ChronoUnit.MINUTES))
    toDeploy.head.scheduleName shouldBe Some("scheduleMinute5")
    toDeploy.last.runAt shouldBe localTime(expectedScheduleTime.plus(10, ChronoUnit.MINUTES))
    toDeploy.last.scheduleName shouldBe Some("scheduleMinute10")

    service.deactivate(processName).futureValue
    service.getLatestDeploymentForActiveSchedulesOld(processName).futureValue shouldBe None
  }

  it should "wait until other schedule finishes, before deploying next schedule" in withFixture() { f =>
    val timeToTrigger = startTime.plus(2, ChronoUnit.HOURS)

    var currentTime = startTime

    def service = f.periodicProcessService(currentTime)

    val firstSchedule = "schedule1"
    val secondSchedule = "schedule2"
    service.schedule(MultipleScheduleProperty(Map(
      firstSchedule -> CronScheduleProperty("0 5 * * * ?"),
      secondSchedule -> CronScheduleProperty("0 5 * * * ?"))),
      ProcessVersion.empty.copy(processName = processName), sampleProcess).futureValue

    currentTime = timeToTrigger

    val toDeploy = service.findToBeDeployed.futureValue
    toDeploy should have length 2

    val deployment = toDeploy.find(_.scheduleName.contains(firstSchedule)).value
    service.deploy(deployment)
    f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.Running, Some(deployment.id))

    val toDeployAfterDeploy = service.findToBeDeployed.futureValue
    toDeployAfterDeploy should have length 0

    f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.Finished, Some(deployment.id))
    service.handleFinished.futureValue

    val toDeployAfterFinish = service.findToBeDeployed.futureValue
    toDeployAfterFinish should have length 1
    toDeployAfterFinish.head.scheduleName.value shouldBe secondSchedule
  }

  it should "handle multiple one time schedules" in withFixture() { f =>
    var currentTime = startTime
    def service = f.periodicProcessService(currentTime)
    val timeToTriggerSchedule1 = startTime.plus(1, ChronoUnit.HOURS)
    val timeToTriggerSchedule2 = startTime.plus(2, ChronoUnit.HOURS)

    service.schedule(MultipleScheduleProperty(Map(
      "schedule1" -> CronScheduleProperty(convertDateToCron(localTime(timeToTriggerSchedule1))),
      "schedule2" -> CronScheduleProperty(convertDateToCron(localTime(timeToTriggerSchedule2))))),
      ProcessVersion.empty.copy(processName = processName), sampleProcess).futureValue

    val latestDeploymentSchedule1 = service.getLatestDeploymentForActiveSchedulesOld(processName).futureValue.value
    latestDeploymentSchedule1.scheduleName.value shouldBe "schedule1"
    latestDeploymentSchedule1.runAt shouldBe localTime(timeToTriggerSchedule1)

    currentTime = timeToTriggerSchedule1

    val toDeployOnSchedule1 = service.findToBeDeployed.futureValue.loneElement
    toDeployOnSchedule1.scheduleName.value shouldBe "schedule1"

    service.deploy(toDeployOnSchedule1).futureValue

    service.getLatestDeploymentForActiveSchedulesOld(processName).futureValue.value.state.status shouldBe PeriodicProcessDeploymentStatus.Deployed

    service.handleFinished.futureValue

    val toDeployAfterFinishSchedule1 = service.findToBeDeployed.futureValue
    toDeployAfterFinishSchedule1 should have length 0
    val latestDeploymentSchedule2 = service.getLatestDeploymentForActiveSchedulesOld(processName).futureValue.value
    latestDeploymentSchedule2.scheduleName.value shouldBe "schedule2"
    latestDeploymentSchedule2.runAt shouldBe localTime(timeToTriggerSchedule2)
    latestDeploymentSchedule2.state.status shouldBe PeriodicProcessDeploymentStatus.Scheduled

    currentTime = timeToTriggerSchedule2

    val toDeployOnSchedule2 = service.findToBeDeployed.futureValue.loneElement
    toDeployOnSchedule2.scheduleName.value shouldBe "schedule2"

    service.deploy(toDeployOnSchedule2).futureValue

    service.getLatestDeploymentForActiveSchedulesOld(processName).futureValue.value.state.status shouldBe PeriodicProcessDeploymentStatus.Deployed

    service.handleFinished.futureValue

    service.getLatestDeploymentForActiveSchedulesOld(processName).futureValue shouldBe None
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

    tryWithFailedListener {
      () => service.schedule(cronEveryHour, ProcessVersion.empty.copy(processName = processName), sampleProcess)
    }

    currentTime = timeToTriggerCheck
    val toDeploy = service.findToBeDeployed.futureValue
    toDeploy should have length 1
    val deployment = toDeploy.head
    service.deploy(deployment).futureValue
    f.delegateDeploymentManagerStub.setStateStatus(SimpleStateStatus.Finished, Some(deployment.id))

    tryWithFailedListener {
      () => service.deactivate(processName)
    }
  }

  it should "reschedule after failed if configured" in withFixture(executionConfig = PeriodicExecutionConfig(rescheduleOnFailure = true)) { f =>
    val timeToTriggerCheck = startTime.plus(1, ChronoUnit.HOURS)
    var currentTime = startTime

    f.jarManagerStub.deployWithJarFuture = Future.failed(new RuntimeException("Flink deploy error"))
    def service = f.periodicProcessService(currentTime)

    service.schedule(cronEveryHour, ProcessVersion.empty.copy(processName = processName), sampleProcess).futureValue
    currentTime = timeToTriggerCheck
    val toDeploy = service.findToBeDeployed.futureValue.toList

    val deployment = toDeploy.head
    service.deploy(deployment).futureValue

    f.delegateDeploymentManagerStub.setStateStatus(ProblemStateStatus.Failed, Some(deployment.id))

    //this one is cyclically called by RescheduleActor
    service.handleFinished.futureValue

    val processDeployed = service.getLatestDeploymentForActiveSchedulesOld(processName).futureValue.value
    processDeployed.state.status shouldBe PeriodicProcessDeploymentStatus.Scheduled
  }

  private def convertDateToCron(date: LocalDateTime): String = {
    CronBuilder.cron(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ))
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

}
