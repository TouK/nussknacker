package pl.touk.nussknacker.engine.management

import org.apache.flink.configuration.{Configuration, CoreOptions}
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.deployment.{ExternalDeploymentId, GraphProcess}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.management.FlinkSlotsChecker.{NotEnoughSlotsException, SlotsBalance}
import pl.touk.nussknacker.engine.management.rest.HttpFlinkClient
import pl.touk.nussknacker.engine.management.rest.flinkRestModel._
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.test.PatientScalaFutures
import sttp.client.testing.SttpBackendStub
import sttp.client.{NothingT, Response, SttpBackend, SttpClientException}
import sttp.model.{Method, StatusCode}

import java.net.ConnectException
import java.util.Collections
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class FlinkSlotsCheckerTest extends FunSuite with Matchers with PatientScalaFutures {

  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  private val config = FlinkConfig("http://test.pl", None)

  private val availableSlotsCount = 1000

  test("check available slots count") {
    val slotsChecker = createSlotsChecker()
    slotsChecker.checkRequiredSlotsExceedAvailableSlots(prepareProcessDeploymentData(Some(availableSlotsCount)), None).futureValue

    val requestedSlotsCount = availableSlotsCount + 1
    slotsChecker.checkRequiredSlotsExceedAvailableSlots(prepareProcessDeploymentData(Some(requestedSlotsCount)), None).failed.futureValue shouldEqual
      NotEnoughSlotsException(availableSlotsCount, availableSlotsCount, SlotsBalance(0, requestedSlotsCount))
  }

  test("take an account of slots that will be released be job that will be cancelled during redeploy") {
    val slotsChecker = createSlotsChecker()
    // +1 because someCurrentJobId uses one slot now
    slotsChecker.checkRequiredSlotsExceedAvailableSlots(prepareProcessDeploymentData(Some(availableSlotsCount + 1)), Some(ExternalDeploymentId("someCurrentJobId"))).futureValue

    val requestedSlotsCount = availableSlotsCount + 2
    slotsChecker.checkRequiredSlotsExceedAvailableSlots(prepareProcessDeploymentData(Some(requestedSlotsCount)),  Some(ExternalDeploymentId("someCurrentJobId"))).failed.futureValue shouldEqual
      NotEnoughSlotsException(availableSlotsCount, availableSlotsCount, SlotsBalance(1, requestedSlotsCount))
  }

  test("check available slots count when parallelism is not defined") {
    val slotsChecker = createSlotsChecker(clusterOverviewResult = Success(ClusterOverview(`slots-total` = 0, `slots-available` = 0)))
    slotsChecker.checkRequiredSlotsExceedAvailableSlots(prepareProcessDeploymentData(None), None).failed.futureValue shouldEqual
      NotEnoughSlotsException(0, 0, SlotsBalance(0, CoreOptions.DEFAULT_PARALLELISM.defaultValue()))
  }

  test("omit slots checking if flink api returned error during cluster overview") {
    val slotsChecker = createSlotsChecker(clusterOverviewResult = Failure(new ConnectException("Some connect error")))
    slotsChecker.checkRequiredSlotsExceedAvailableSlots(prepareProcessDeploymentData(Some(availableSlotsCount)), None).futureValue
  }

  test("omit slots checking if flink api returned error during jobmanager config") {
    val slotsChecker = createSlotsChecker(jobManagerConfigResult = Failure(new ConnectException("Some connect error")))
    slotsChecker.checkRequiredSlotsExceedAvailableSlots(prepareProcessDeploymentData(None), None).futureValue
  }

  private def createSlotsChecker(statuses: List[JobOverview] = List(),
                                 statusCode: StatusCode = StatusCode.Ok,
                                 clusterOverviewResult: Try[ClusterOverview] = Success(ClusterOverview(`slots-total` = 1000, `slots-available` = availableSlotsCount)),
                                 jobManagerConfigResult: Try[Configuration] = Success(Configuration.fromMap(Collections.emptyMap())) // be default used config with all default values
                                ): FlinkSlotsChecker = {
    import scala.collection.JavaConverters._
    val slotsChecker = createSlotsCheckerWithBackend(SttpBackendStub.asynchronousFuture.whenRequestMatchesPartial { case req =>
      val toReturn = (req.uri.path, req.method) match {
        case (List("jobs", "overview"), Method.GET) =>
          JobsResponse(statuses)
        case (List("jobs", jobId, "config"), Method.GET) =>
          JobConfig(jobId, ExecutionConfig(`job-parallelism` = 1, `user-config` = Map.empty))
        case (List("overview"), Method.GET) =>
          clusterOverviewResult.recoverWith {
            case ex: Exception => Failure(SttpClientException.defaultExceptionToSttpClientException(ex).get)
          }.get
        case (List("jobmanager", "config"), Method.GET) =>
          jobManagerConfigResult.map(_.toMap.asScala.toList.map {
            case (key, value) => KeyValueEntry(key, value)
          }).recoverWith {
            case ex: Exception => Failure(SttpClientException.defaultExceptionToSttpClientException(ex).get)
          }.get
      }
      Response(Right(toReturn), statusCode)
    })
    slotsChecker
  }

  private def createSlotsCheckerWithBackend(backend: SttpBackend[Future, Nothing, NothingT]): FlinkSlotsChecker = {
    implicit val b: SttpBackend[Future, Nothing, NothingT] = backend
    new FlinkSlotsChecker(new HttpFlinkClient(config))
  }

  private def prepareProcessDeploymentData(parallelism: Option[Int]) = {
    val baseProcessBuilder = EspProcessBuilder.id("processTestingTMSlots")
    val process = parallelism.map(baseProcessBuilder.parallelism).getOrElse(baseProcessBuilder)
      .source("startProcess", "kafka-transaction")
      .emptySink("endSend", "sendSms")
    val processDeploymentData = GraphProcess(ProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).spaces2)
    processDeploymentData
  }

}
