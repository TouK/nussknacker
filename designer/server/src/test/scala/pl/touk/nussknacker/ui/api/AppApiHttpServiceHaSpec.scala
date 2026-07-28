package pl.touk.nussknacker.ui.api

import com.typesafe.config.{Config, ConfigFactory}
import io.restassured.RestAssured._
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.tags.Slow
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.test.NuRestAssureExtensions._
import pl.touk.nussknacker.test.RestAssuredVerboseLoggingIfValidationFails
import pl.touk.nussknacker.test.base.it.NuItTestWithPostgres
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig

import scala.util.Using

@Slow
class AppApiHttpServiceHaSpec
    extends AnyFreeSpecLike
    with BeforeAndAfterEach
    with Eventually
    with NuItTestWithPostgres
    with WithSimplifiedDesignerConfig
    with RestAssuredVerboseLoggingIfValidationFails {

  override protected def afterEach(): Unit = {
    Using(testDbRef.db.createSession()) { session =>
      session
        .prepareStatement(
          s"""UPDATE "${getSchemaName()}"."distributed_locks"
             |SET lock_until = LOCALTIMESTAMP - INTERVAL '1 second'
             |WHERE name = 'designer-leader'""".stripMargin
        )
        .execute()
    }.get
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
      assertIsLeader(expected = true)

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
  }

  private def assertIsLeader(expected: Boolean): Unit =
    given()
      .when()
      .noAuth()
      .get(s"$nuDesignerHttpAddress/api/app/leader")
      .Then()
      .statusCode(200)
      .equalsJsonBody(
        s"""{
           |  "instanceId": "test-instance",
           |  "isHaEnabled": true,
           |  "isLeader": $expected
           |}""".stripMargin
      )

}
