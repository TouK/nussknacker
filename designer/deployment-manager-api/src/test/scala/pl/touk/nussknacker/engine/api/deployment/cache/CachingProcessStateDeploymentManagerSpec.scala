package pl.touk.nussknacker.engine.api.deployment.cache

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.invocation.InvocationOnMock
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.test.PatientScalaFutures

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration._

class CachingProcessStateDeploymentManagerSpec
    extends AnyFunSuite
    with MockitoSugar
    with PatientScalaFutures
    with Matchers
    with OptionValues {

  test("should ask delegate for a fresh state each time") {
    val delegate = prepareDMReturningRandomStates
    val cachingManager = new CachingProcessStateDeploymentManager(
      delegate,
      10 seconds,
      NoDeploymentSynchronisationSupport,
      NoStateQueryForAllScenariosSupport
    )

    val results = List(
      cachingManager.getProcessStatesDeploymentIdNow(DataFreshnessPolicy.Fresh),
      cachingManager.getProcessStatesDeploymentIdNow(DataFreshnessPolicy.Fresh)
    )
    results.map(_.cached) should contain only false
    results.map(_.value).distinct should have size 2

    verify(delegate, times(2)).getProcessStates(any[ProcessName])(any[DataFreshnessPolicy])
  }

  test("should cache state for DataFreshnessPolicy.CanBeCached") {
    val delegate = prepareDMReturningRandomStates
    val cachingManager = new CachingProcessStateDeploymentManager(
      delegate,
      10 seconds,
      NoDeploymentSynchronisationSupport,
      NoStateQueryForAllScenariosSupport
    )

    val firstInvocation = cachingManager.getProcessStatesDeploymentIdNow(DataFreshnessPolicy.CanBeCached)
    firstInvocation.cached shouldBe false
    val secondInvocation = cachingManager.getProcessStatesDeploymentIdNow(DataFreshnessPolicy.CanBeCached)
    secondInvocation.cached shouldBe true
    List(firstInvocation, secondInvocation).map(_.value).distinct should have size 1

    verify(delegate, times(1)).getProcessStates(any[ProcessName])(any[DataFreshnessPolicy])
  }

  test("should reuse state updated by DataFreshnessPolicy.Fresh during reading with DataFreshnessPolicy.CanBeCached") {
    val delegate = prepareDMReturningRandomStates
    val cachingManager = new CachingProcessStateDeploymentManager(
      delegate,
      10 seconds,
      NoDeploymentSynchronisationSupport,
      NoStateQueryForAllScenariosSupport
    )

    val resultForFresh = cachingManager.getProcessStatesDeploymentIdNow(DataFreshnessPolicy.Fresh)
    resultForFresh.cached shouldBe false
    val resultForCanBeCached = cachingManager.getProcessStatesDeploymentIdNow(DataFreshnessPolicy.CanBeCached)
    resultForCanBeCached.cached shouldBe true
    List(resultForFresh, resultForCanBeCached).map(_.value).distinct should have size 1

    verify(delegate, times(1)).getProcessStates(any[ProcessName])(any[DataFreshnessPolicy])
  }

  implicit class DeploymentManagerOps(dm: DeploymentManager) {

    def getProcessStatesDeploymentIdNow(freshnessPolicy: DataFreshnessPolicy): WithDataFreshnessStatus[List[String]] =
      dm.getProcessStates(ProcessName("foo"))(freshnessPolicy)
        .futureValue
        .map(_.map(_.externalDeploymentId.value.value))

  }

  private def prepareDMReturningRandomStates: DeploymentManager = {
    val delegate = mock[DeploymentManager]
    when(delegate.getProcessStates(any[ProcessName])(any[DataFreshnessPolicy])).thenAnswer { _: InvocationOnMock =>
      val randomState = StatusDetails(
        SimpleStateStatus.Running,
        deploymentId = None,
        externalDeploymentId = Some(ExternalDeploymentId(UUID.randomUUID().toString))
      )
      Future.successful(WithDataFreshnessStatus.fresh(List(randomState)))
    }
    delegate
  }

}
