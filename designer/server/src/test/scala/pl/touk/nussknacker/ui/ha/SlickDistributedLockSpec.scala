package pl.touk.nussknacker.ui.ha

import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Slow
import pl.touk.nussknacker.test.base.db.WithPostgresDbTesting

import java.time.{Clock, Instant}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Using

@Slow
class SlickDistributedLockSpec extends AnyFunSuite with Matchers with WithPostgresDbTesting with BeforeAndAfterEach {

  private val clock    = Clock.systemUTC()
  private val lockName = "test-lock"
  private val duration = 30.seconds

  private val clockSkewTolerance = 1.second

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    Using(testDbRef.db.createSession()) { session =>
      session.prepareStatement(s"""TRUNCATE TABLE "${getSchemaName()}".distributed_locks""").execute()
    }.get
  }

  private def lock(instanceId: String) = SlickDistributedLock(testDbRef, instanceId, lockQueryTimeout = 5.seconds)
  private lazy val inst1               = lock("instance-1")
  private lazy val inst2               = lock("instance-2")

  // Runs acquireOrRenew and asserts that validUntil ≈ now + duration.
  // Timing is captured internally so tests don't need to manage it.
  private def assertAcquired(call: => Future[Option[Instant]]): Unit = {
    val before     = clock.instant()
    val validUntil = call.futureValue.getOrElse(fail("expected lock to be acquired, but got None"))
    val after      = clock.instant()
    validUntil should be >= before.plusMillis(duration.toMillis - clockSkewTolerance.toMillis)
    validUntil should be <= after.plusMillis(duration.toMillis + clockSkewTolerance.toMillis)
  }

  // --- acquireOrRenew ---

  test("acquireOrRenew returns lock_until ≈ now + duration for a free lock") {
    assertAcquired(inst1.acquireOrRenew(lockName, duration))
  }

  test("acquireOrRenew returns None when lock is held by another instance") {
    assertAcquired(inst1.acquireOrRenew(lockName, duration))
    inst2.acquireOrRenew(lockName, duration).futureValue shouldBe None
    assertAcquired(inst1.acquireOrRenew(lockName, duration))
  }

  test("acquireOrRenew returns lock_until ≈ now + duration on renewal by same instance") {
    assertAcquired(inst1.acquireOrRenew(lockName, duration))
    assertAcquired(inst1.acquireOrRenew(lockName, duration))
  }

  test("acquireOrRenew returns lock_until ≈ now + duration when the previous lock has expired") {
    insertExpiredLock()
    assertAcquired(inst2.acquireOrRenew(lockName, duration))
  }

  // --- release ---

  test("release allows another instance to acquire the lock") {
    assertAcquired(inst1.acquireOrRenew(lockName, duration))
    inst1.release(lockName).futureValue shouldBe true
    assertAcquired(inst2.acquireOrRenew(lockName, duration))
  }

  test("release by non-holder does not release the lock") {
    assertAcquired(inst1.acquireOrRenew(lockName, duration))
    inst2.release(lockName).futureValue shouldBe false
    inst2.acquireOrRenew(lockName, duration).futureValue shouldBe None
  }

  test("release is idempotent — second call returns false") {
    assertAcquired(inst1.acquireOrRenew(lockName, duration))
    inst1.release(lockName).futureValue shouldBe true
    inst1.release(lockName).futureValue shouldBe false
  }

  test("acquireOrRenew can re-acquire own lock after release (locked_by bypass)") {
    assertAcquired(inst1.acquireOrRenew(lockName, duration))
    inst1.release(lockName).futureValue shouldBe true
    // After release lock_until = now — both conditions in WHERE match for inst1
    assertAcquired(inst1.acquireOrRenew(lockName, duration))
  }

  private def insertExpiredLock(): Unit = {
    Using(testDbRef.db.createSession()) { session =>
      session
        .prepareStatement(
          s"""INSERT INTO "${getSchemaName()}".distributed_locks (name, lock_until, locked_at, locked_by)
             |VALUES ('$lockName', CURRENT_TIMESTAMP - INTERVAL '1 second', CURRENT_TIMESTAMP - INTERVAL '60 seconds', 'instance-1')""".stripMargin
        )
        .execute()
    }.get
  }

}
