package pl.touk.nussknacker.engine.management.rest

import io.circe.Json
import org.apache.flink.api.common.{JobID, JobStatus}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.invocation.InvocationOnMock
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, WithDataFreshnessStatus}
import pl.touk.nussknacker.engine.management.rest.flinkRestModel.{JobOverview, JobTasksOverview}
import pl.touk.nussknacker.engine.management.utils.JobIdGenerator.generateJobId
import pl.touk.nussknacker.test.PatientScalaFutures

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration._

class CachedFlinkClientTest
    extends AnyFunSuite
    with MockitoSugar
    with PatientScalaFutures
    with Matchers
    with OptionValues {

  private val sampleJobId = generateJobId

  test("should ask delegate for a fresh jobs by name each time") {
    val delegate           = prepareMockedFlinkClient
    val cachingFlinkClient = new CachedFlinkClient(delegate, 10 seconds, 10)

    val results = List(
      cachingFlinkClient.getJobsOverviews()(DataFreshnessPolicy.Fresh).futureValue,
      cachingFlinkClient.getJobsOverviews()(DataFreshnessPolicy.Fresh).futureValue,
    )

    results.map(_.cached) should contain only false

    verify(delegate, times(2)).getJobsOverviews()(any[DataFreshnessPolicy])
  }

  test("should cache jobs by name for DataFreshnessPolicy.CanBeCached") {
    val delegate           = prepareMockedFlinkClient
    val cachingFlinkClient = new CachedFlinkClient(delegate, 10 seconds, 10)

    val results = List(
      cachingFlinkClient.getJobsOverviews()(DataFreshnessPolicy.CanBeCached).futureValue,
      cachingFlinkClient.getJobsOverviews()(DataFreshnessPolicy.CanBeCached).futureValue,
    )

    results.map(_.cached) should contain allOf (false, true)

    verify(delegate, times(1)).getJobsOverviews()(any[DataFreshnessPolicy])
  }

  test("should cache job configs by default") {
    val delegate           = prepareMockedFlinkClient
    val cachingFlinkClient = new CachedFlinkClient(delegate, 10 seconds, 10)

    val results = List(
      cachingFlinkClient.getJobConfig(sampleJobId).futureValue,
      cachingFlinkClient.getJobConfig(sampleJobId).futureValue,
      cachingFlinkClient.getJobConfig(sampleJobId).futureValue,
    )

    results.map(_.`user-config`.get("time")).distinct should have size 1

    verify(delegate, times(1)).getJobConfig(any[JobID])
  }

  test("shouldn't cache job configs with missing deploymentId") {
    val delegate           = mock[FlinkClient]
    val cachingFlinkClient = new CachedFlinkClient(delegate, 10 seconds, 10)

    when(delegate.getJobConfig(any[JobID])).thenAnswer { _: InvocationOnMock =>
      val config = flinkRestModel.ExecutionConfig(
        `job-parallelism` = 1,
        `user-config` = Map.empty
      )
      Future.successful(config)
    }
    cachingFlinkClient
      .getJobConfig(sampleJobId)
      .futureValue
      .`user-config`
      .get(CachedFlinkClient.DeploymentIdUserConfigKey) shouldBe empty

    when(delegate.getJobConfig(any[JobID])).thenAnswer { _: InvocationOnMock =>
      val config = flinkRestModel.ExecutionConfig(
        `job-parallelism` = 1,
        `user-config` = Map(CachedFlinkClient.DeploymentIdUserConfigKey -> Json.fromString("someDeploymentId"))
      )
      Future.successful(config)
    }
    cachingFlinkClient
      .getJobConfig(sampleJobId)
      .futureValue
      .`user-config`
      .get(CachedFlinkClient.DeploymentIdUserConfigKey) shouldBe defined
  }

  private def prepareMockedFlinkClient: FlinkClient = {
    val delegate = mock[FlinkClient]

    when(delegate.getJobsOverviews()(any[DataFreshnessPolicy])).thenAnswer { _: InvocationOnMock =>
      val jobs = List(
        JobOverview(
          sampleJobId,
          "p1",
          10L,
          10L,
          JobStatus.RUNNING.name(),
          tasksOverview(running = 1)
        )
      )

      Future.successful(WithDataFreshnessStatus.fresh(jobs))
    }

    when(delegate.getJobConfig(any[JobID])).thenAnswer { _: InvocationOnMock =>
      val config = flinkRestModel.ExecutionConfig(
        `job-parallelism` = 1,
        `user-config` = Map(
          "time"                                      -> Json.fromLong(System.currentTimeMillis()),
          CachedFlinkClient.DeploymentIdUserConfigKey -> Json.fromString("fooDeploymentId")
        )
      )

      Future.successful(config)
    }

    delegate
  }

  private def tasksOverview(
      total: Int = 1,
      created: Int = 0,
      scheduled: Int = 0,
      deploying: Int = 0,
      running: Int = 0,
      finished: Int = 0,
      canceling: Int = 0,
      canceled: Int = 0,
      failed: Int = 0,
      reconciling: Int = 0,
      initializing: Int = 0
  ): JobTasksOverview =
    JobTasksOverview(
      total,
      created = created,
      scheduled = scheduled,
      deploying = deploying,
      running = running,
      finished = finished,
      canceling = canceling,
      canceled = canceled,
      failed = failed,
      reconciling = reconciling,
      initializing = Some(initializing)
    )

}
