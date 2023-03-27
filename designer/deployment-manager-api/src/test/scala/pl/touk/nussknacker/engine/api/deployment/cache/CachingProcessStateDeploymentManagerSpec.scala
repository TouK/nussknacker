package pl.touk.nussknacker.engine.api.deployment.cache

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.invocation.InvocationOnMock
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, DeploymentManager, ProcessState, StateStatus, WithDataFreshnessStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.deployment.ExternalDeploymentId
import pl.touk.nussknacker.test.PatientScalaFutures

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration._

class CachingProcessStateDeploymentManagerSpec extends AnyFunSuite with MockitoSugar with PatientScalaFutures
  with Matchers with OptionValues {

  test("should ask delegate for a fresh state each time") {
    val delegate = prepareDMReturningRandomStates
    val cachingManager = new CachingProcessStateDeploymentManager(delegate, 10 seconds)

    val results = List(
      cachingManager.getProcessStateDeploymentIdNow(DataFreshnessPolicy.Fresh),
      cachingManager.getProcessStateDeploymentIdNow(DataFreshnessPolicy.Fresh))
    results.map(_.cached) should contain only false
    results.map(_.value).distinct should have size 2

    verify(delegate, times(2)).getProcessState(any[ProcessName])(any[DataFreshnessPolicy])
  }

  test("should cache state for DataFreshnessPolicy.CanBeCached") {
    val delegate = prepareDMReturningRandomStates
    val cachingManager = new CachingProcessStateDeploymentManager(delegate, 10 seconds)

    val firstInvocation = cachingManager.getProcessStateDeploymentIdNow(DataFreshnessPolicy.CanBeCached)
    firstInvocation.cached shouldBe false
    val secondInvocation = cachingManager.getProcessStateDeploymentIdNow(DataFreshnessPolicy.CanBeCached)
    secondInvocation.cached shouldBe true
    List(firstInvocation, secondInvocation).map(_.value).distinct should have size 1

    verify(delegate, times(1)).getProcessState(any[ProcessName])(any[DataFreshnessPolicy])
  }

  test("should reuse state updated by DataFreshnessPolicy.Fresh during reading with DataFreshnessPolicy.CanBeCached") {
    val delegate = prepareDMReturningRandomStates
    val cachingManager = new CachingProcessStateDeploymentManager(delegate, 10 seconds)

    val resultForFresh = cachingManager.getProcessStateDeploymentIdNow(DataFreshnessPolicy.Fresh)
    resultForFresh.cached shouldBe false
    val resultForCanBeCached = cachingManager.getProcessStateDeploymentIdNow(DataFreshnessPolicy.CanBeCached)
    resultForCanBeCached.cached shouldBe true
    List(resultForFresh, resultForCanBeCached).map(_.value).distinct should have size 1

    verify(delegate, times(1)).getProcessState(any[ProcessName])(any[DataFreshnessPolicy])
  }

  implicit class DeploymentManagerOps(dm: DeploymentManager) {
    def getProcessStateDeploymentIdNow(freshnessPolicy: DataFreshnessPolicy): WithDataFreshnessStatus[String] =
      dm.getProcessState(ProcessName("foo"))(freshnessPolicy).futureValue.map(_.value.deploymentId.value.value)
  }

  private def prepareDMReturningRandomStates = {
    val delegate = mock[DeploymentManager]
    when(delegate.getProcessState(any[ProcessName])(any[DataFreshnessPolicy])).thenAnswer { _: InvocationOnMock =>
      val randomState = SimpleProcessStateDefinitionManager.processState(
        SimpleStateStatus.Running,
        deploymentId = Some(ExternalDeploymentId(UUID.randomUUID().toString)))
      Future.successful(WithDataFreshnessStatus[Option[ProcessState]](Some(randomState), cached = false))
    }
    delegate
  }

}
