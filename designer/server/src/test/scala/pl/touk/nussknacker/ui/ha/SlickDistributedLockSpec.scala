package pl.touk.nussknacker.ui.ha

import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Slow
import pl.touk.nussknacker.test.base.db.WithPostgresDbTesting

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Using

@Slow
class SlickDistributedLockSpec extends AnyFunSuite with Matchers with WithPostgresDbTesting with BeforeAndAfterEach {

  private val lockName = "test-lock"
  private val duration = 30.seconds

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    Using(testDbRef.db.createSession()) { session =>
      session.prepareStatement(s"""DELETE FROM "${getSchemaName()}".distributed_locks""").execute()
    }
  }

  private def lock(instanceId: String) = SlickDistributedLock(testDbRef, instanceId, lockQueryTimeout = 5.seconds)
  private lazy val inst1               = lock("instance-1")
  private lazy val inst2               = lock("instance-2")

  // --- acquireOrRenew ---

  test("acquireOrRenew returns true for a free lock") {
    inst1.acquireOrRenew(lockName, duration).futureValue shouldBe true
  }

  test("acquireOrRenew returns false when lock is held by another instance") {
    inst1.acquireOrRenew(lockName, duration).futureValue shouldBe true
    inst2.acquireOrRenew(lockName, duration).futureValue shouldBe false
    inst1.acquireOrRenew(lockName, duration).futureValue shouldBe true
  }

  test("acquireOrRenew returns true when held by the same instance (owner bypass, lock not expired)") {
    inst1.acquireOrRenew(lockName, duration).futureValue shouldBe true
    inst1.acquireOrRenew(lockName, duration).futureValue shouldBe true
  }

  test("acquireOrRenew returns true when the lock has expired") {
    insertExpiredLock(holder = "instance-1")
    inst2.acquireOrRenew(lockName, duration).futureValue shouldBe true
  }

  // --- release ---

  test("release allows another instance to acquire the lock") {
    inst1.acquireOrRenew(lockName, duration).futureValue shouldBe true
    inst1.release(lockName).futureValue
    inst2.acquireOrRenew(lockName, duration).futureValue shouldBe true
  }

  test("release by non-holder does not release the lock") {
    inst1.acquireOrRenew(lockName, duration).futureValue shouldBe true
    inst2.release(lockName).futureValue
    inst2.acquireOrRenew(lockName, duration).futureValue shouldBe false
  }

  test("acquireOrRenew can re-acquire own lock after release (locked_by bypass)") {
    inst1.acquireOrRenew(lockName, duration).futureValue shouldBe true
    inst1.release(lockName).futureValue
    // After release lock_until = now — both conditions in WHERE match for inst1
    inst1.acquireOrRenew(lockName, duration).futureValue shouldBe true
  }

  private def insertExpiredLock(holder: String): Unit = {
    Using(testDbRef.db.createSession()) { session =>
      session
        .prepareStatement(
          s"""INSERT INTO "${getSchemaName()}".distributed_locks (name, lock_until, locked_at, locked_by)
             |VALUES ('$lockName', CURRENT_TIMESTAMP - INTERVAL '1 second', CURRENT_TIMESTAMP - INTERVAL '60 seconds', '$holder')""".stripMargin
        )
        .execute()
    }
  }

}
