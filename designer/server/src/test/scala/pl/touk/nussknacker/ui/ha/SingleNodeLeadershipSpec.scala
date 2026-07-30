package pl.touk.nussknacker.ui.ha

import cats.effect.IO
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.engine.util.ExecutionContextWithIORuntimeAdapter

import scala.concurrent.ExecutionContext

class SingleNodeLeadershipSpec extends AnyFunSuite with Matchers with BeforeAndAfterAll with Eventually {

  private implicit val executionContextWithIORuntime: ExecutionContextWithIORuntimeAdapter =
    ExecutionContextWithIORuntimeAdapter.unsafeCreateFrom(ExecutionContext.global)

  import executionContextWithIORuntime.ioRuntime

  override def afterAll(): Unit = executionContextWithIORuntime.close()

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = Span(5, Seconds),
    interval = Span(50, Millis)
  )

  test("isLeader always returns true") {
    new SingleNodeLeadership(instanceId = "test").isLeader() shouldBe true
  }

  test("haEnabled returns false") {
    new SingleNodeLeadership(instanceId = "test").haEnabled shouldBe false
  }

  test("instanceId returns the configured value") {
    new SingleNodeLeadership(instanceId = "my-node").instanceId shouldBe "my-node"
  }

  test("onLeadershipAcquired fires callback when startHeartbeat is called") {
    @volatile var fired = false
    val service         = new SingleNodeLeadership(instanceId = "test")
    service.onLeadershipAcquired(IO { fired = true }).unsafeRunSync()
    service.startHeartbeat().use(_ => IO(eventually { fired shouldBe true })).unsafeRunSync()
  }

  test("onLeadershipAcquired fires all registered callbacks when startHeartbeat is called") {
    @volatile var count1 = 0
    @volatile var count2 = 0
    val service          = new SingleNodeLeadership(instanceId = "test")
    service.onLeadershipAcquired(IO { count1 += 1 }).unsafeRunSync()
    service.onLeadershipAcquired(IO { count2 += 1 }).unsafeRunSync()
    service
      .startHeartbeat()
      .use(_ =>
        IO {
          eventually { count1 shouldBe 1 }
          eventually { count2 shouldBe 1 }
        }
      )
      .unsafeRunSync()
  }

  test("onLeadershipAcquired fires each callback exactly once") {
    @volatile var count = 0
    val service         = new SingleNodeLeadership(instanceId = "test")
    service.onLeadershipAcquired(IO { count += 1 }).unsafeRunSync()
    service
      .startHeartbeat()
      .use(_ =>
        IO {
          eventually { count shouldBe 1 }
          Thread.sleep(200)
          count shouldBe 1
        }
      )
      .unsafeRunSync()
  }

  test("onLeadershipLost is a no-op") {
    @volatile var fired = false
    val service         = new SingleNodeLeadership(instanceId = "test")
    service.onLeadershipLost(IO { fired = true }).unsafeRunSync()
    service
      .startHeartbeat()
      .use(_ => IO { Thread.sleep(100); fired shouldBe false })
      .unsafeRunSync()
  }

  test("onLeadershipAcquired raises IllegalStateException when registered after startHeartbeat") {
    val service = new SingleNodeLeadership(instanceId = "test")
    service
      .startHeartbeat()
      .use(_ =>
        IO {
          the[IllegalStateException] thrownBy service.onLeadershipAcquired(IO.unit).unsafeRunSync() should have message
            "Callbacks must be registered before startHeartbeat() is called"
        }
      )
      .unsafeRunSync()
  }

  test("onLeadershipLost raises IllegalStateException when registered after startHeartbeat") {
    val service = new SingleNodeLeadership(instanceId = "test")
    service
      .startHeartbeat()
      .use(_ =>
        IO {
          the[IllegalStateException] thrownBy service.onLeadershipLost(IO.unit).unsafeRunSync() should have message
            "Callbacks must be registered before startHeartbeat() is called"
        }
      )
      .unsafeRunSync()
  }

}
