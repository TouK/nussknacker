package pl.touk.nussknacker.engine.management

import org.apache.flink.api.common.JobID
import org.apache.flink.configuration.{Configuration, CoreOptions}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.management.FlinkSlotsChecker.{NotEnoughSlotsException, SlotsBalance}
import pl.touk.nussknacker.engine.management.rest.HttpFlinkClient
import pl.touk.nussknacker.engine.management.rest.flinkRestModel._
import pl.touk.nussknacker.engine.management.utils.JobIdGenerator.generateJobId
import pl.touk.nussknacker.test.PatientScalaFutures
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{Response, SttpBackend, SttpClientException}
import sttp.model.{Method, StatusCode}

import java.net.{ConnectException, URI}
import java.util.{Collections, UUID}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class FlinkSlotsCheckerTest extends AnyFunSuite with Matchers with PatientScalaFutures {

  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  private val availableSlotsCount = 1000

  test("check available slots count") {
    val slotsChecker = createSlotsChecker()
    slotsChecker
      .checkRequiredSlotsExceedAvailableSlots(prepareCanonicalProcess(Some(availableSlotsCount)), List.empty)
      .futureValue

    val requestedSlotsCount = availableSlotsCount + 1
    slotsChecker
      .checkRequiredSlotsExceedAvailableSlots(prepareCanonicalProcess(Some(requestedSlotsCount)), List.empty)
      .failed
      .futureValue shouldEqual
      NotEnoughSlotsException(availableSlotsCount, availableSlotsCount, SlotsBalance(0, requestedSlotsCount))
  }

  test("take an account of slots that will be released be job that will be cancelled during redeploy") {
    val slotsChecker     = createSlotsChecker()
    val someCurrentJobId = generateJobId
    // +1 because someCurrentJobId uses one slot now
    slotsChecker
      .checkRequiredSlotsExceedAvailableSlots(
        prepareCanonicalProcess(Some(availableSlotsCount + 1)),
        List(someCurrentJobId)
      )
      .futureValue

    val requestedSlotsCount = availableSlotsCount + 2
    slotsChecker
      .checkRequiredSlotsExceedAvailableSlots(
        prepareCanonicalProcess(Some(requestedSlotsCount)),
        List(someCurrentJobId)
      )
      .failed
      .futureValue shouldEqual
      NotEnoughSlotsException(availableSlotsCount, availableSlotsCount, SlotsBalance(1, requestedSlotsCount))
  }

  test("check available slots count when parallelism is not defined") {
    val slotsChecker =
      createSlotsChecker(clusterOverviewResult = Success(ClusterOverview(`slots-total` = 0, `slots-available` = 0)))
    slotsChecker
      .checkRequiredSlotsExceedAvailableSlots(prepareCanonicalProcess(None), List.empty)
      .failed
      .futureValue shouldEqual
      NotEnoughSlotsException(0, 0, SlotsBalance(0, CoreOptions.DEFAULT_PARALLELISM.defaultValue()))
  }

  test("omit slots checking if flink api returned error during cluster overview") {
    val slotsChecker = createSlotsChecker(clusterOverviewResult = Failure(new ConnectException("Some connect error")))
    slotsChecker
      .checkRequiredSlotsExceedAvailableSlots(prepareCanonicalProcess(Some(availableSlotsCount)), List.empty)
      .futureValue
  }

  test("omit slots checking if flink api returned error during jobmanager config") {
    val slotsChecker = createSlotsChecker(jobManagerConfigResult = Failure(new ConnectException("Some connect error")))
    slotsChecker.checkRequiredSlotsExceedAvailableSlots(prepareCanonicalProcess(None), List.empty).futureValue
  }

  private def createSlotsChecker(
      statuses: List[JobOverview] = List(),
      statusCode: StatusCode = StatusCode.Ok,
      clusterOverviewResult: Try[ClusterOverview] = Success(
        ClusterOverview(`slots-total` = 1000, `slots-available` = availableSlotsCount)
      ),
      jobManagerConfigResult: Try[Configuration] = Success(
        Configuration.fromMap(Collections.emptyMap())
      ) // be default used config with all default values
  ): FlinkSlotsChecker = {
    import scala.jdk.CollectionConverters._
    val slotsChecker = createSlotsCheckerWithBackend(SttpBackendStub.asynchronousFuture.whenRequestMatchesPartial {
      case req =>
        val toReturn = (req.uri.path, req.method) match {
          case (List("jobs", "overview"), Method.GET) =>
            JobsResponse(statuses)
          case (List("jobs", jobIdString, "config"), Method.GET) =>
            JobConfig(
              JobID.fromHexString(jobIdString),
              ExecutionConfig(`job-parallelism` = 1, `user-config` = Map.empty)
            )
          case (List("overview"), Method.GET) =>
            clusterOverviewResult.recoverWith { case ex: Exception =>
              Failure(SttpClientException.defaultExceptionToSttpClientException(req, ex).get)
            }.get
          case (List("jobmanager", "config"), Method.GET) =>
            jobManagerConfigResult
              .map(_.toMap.asScala.toList.map { case (key, value) =>
                KeyValueEntry(key, value)
              })
              .recoverWith { case ex: Exception =>
                Failure(SttpClientException.defaultExceptionToSttpClientException(req, ex).get)
              }
              .get
          case _ => throw new IllegalStateException()
        }
        Response(Right(toReturn), statusCode)
    })
    slotsChecker
  }

  private def createSlotsCheckerWithBackend(backend: SttpBackend[Future, Any]): FlinkSlotsChecker = {
    implicit val b: SttpBackend[Future, Any] = backend
    val client = new HttpFlinkClient(new URI("http://localhost:12345/"), 10.seconds, 10.seconds)
    new FlinkSlotsChecker(client)
  }

  private def prepareCanonicalProcess(parallelism: Option[Int]) = {
    val baseProcessBuilder = ScenarioBuilder.streaming("processTestingTMSlots")
    parallelism
      .map(baseProcessBuilder.parallelism)
      .getOrElse(baseProcessBuilder)
      .source("startProcess", "kafka-transaction")
      .emptySink("endSend", "sendSms")
  }

}
