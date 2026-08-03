package pl.touk.nussknacker.ui.api

import com.typesafe.config.{Config, ConfigFactory}
import io.restassured.RestAssured._
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Slow
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.test.NuRestAssureExtensions._
import pl.touk.nussknacker.test.RestAssuredVerboseLoggingIfValidationFails
import pl.touk.nussknacker.test.base.it.NuItTestWithPostgres
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig

import java.time.Instant
import scala.util.Using

@Slow
class AppApiHttpServiceHaSpec
    extends AnyFreeSpecLike
    with Matchers
    with BeforeAndAfterEach
    with Eventually
    with NuItTestWithPostgres
    with WithSimplifiedDesignerConfig
    with RestAssuredVerboseLoggingIfValidationFails {

  override protected def afterEach(): Unit = {
    expireLeaderLockInDb()
    super.afterEach()
  }

  override def designerRawConfig: Config =
    ConfigFactory
      .parseString(
        """ha {
          |  enabled: true
          |  instanceId: "test-instance"
          |  leader {
          |    leaseDuration: 30s
          |    heartbeatInterval: 2s
          |  }
          |  lockQueryTimeout: 1s
          |}""".stripMargin
      )
      .withFallback(super.designerRawConfig)

  "The app leader endpoint should" - {
    "return isLeader=true with instanceId when HA is enabled and this node wins leadership" in {
      assertIsLeader(expected = true)
    }

    "return isLeader=false with instanceId when another instance holds the lock" in {
      eventually { assertIsLeader(expected = true) }

      Using(testDbRef.db.createSession()) { session =>
        session
          .prepareStatement(
            s"""UPDATE "${getSchemaName()}"."distributed_locks"
               |SET locked_by = 'intruder', lock_until = NOW() + INTERVAL '5 minutes'
               |WHERE name = 'designer-leader'""".stripMargin
          )
          .execute()
      }.get

      eventually(timeout(Span(15, Seconds)), interval(Span(500, Millis))) {
        assertIsLeader(expected = false)
      }
    }

    "re-acquire the lock after it has been externally expired" in {
      eventually { assertIsLeader(expected = true) }
      expireLeaderLockInDb()

      // Verify re-acquisition at the DB level: the isLeader endpoint would pass on the cached in-memory
      // lease (still valid for the lease duration) and could not detect a re-acquisition regression.
      eventually(timeout(Span(15, Seconds)), interval(Span(500, Millis))) {
        fetchLeaderLock() match {
          case Some((lockedBy, lockUntil)) =>
            lockedBy shouldBe "test-instance"
            lockUntil.isAfter(Instant.now()) shouldBe true
          case None => fail("expected leader lock row to exist")
        }
      }
    }
  }

  private def expireLeaderLockInDb(): Unit =
    Using(testDbRef.db.createSession()) { session =>
      session
        .prepareStatement(
          s"""UPDATE "${getSchemaName()}"."distributed_locks"
             |SET lock_until = CURRENT_TIMESTAMP - INTERVAL '1 second'
             |WHERE name = 'designer-leader'""".stripMargin
        )
        .execute()
    }.get

  private def fetchLeaderLock(): Option[(String, Instant)] =
    Using(testDbRef.db.createSession()) { session =>
      val resultSet = session
        .prepareStatement(
          s"""SELECT locked_by, lock_until FROM "${getSchemaName()}"."distributed_locks"
             |WHERE name = 'designer-leader'""".stripMargin
        )
        .executeQuery()
      if (resultSet.next()) Some((resultSet.getString("locked_by"), resultSet.getTimestamp("lock_until").toInstant))
      else None
    }.get

  private def assertIsLeader(expected: Boolean): Unit =
    given()
      .when()
      .noAuth()
      .get(s"$nuDesignerHttpAddress/api/app/leader")
      .Then()
      .statusCode(200)
      .equalsJsonBody(
        s"""{
           |  "haEnabled": true,
           |  "isLeader": $expected,
           |  "instanceId": "test-instance"
           |}""".stripMargin
      )

}
