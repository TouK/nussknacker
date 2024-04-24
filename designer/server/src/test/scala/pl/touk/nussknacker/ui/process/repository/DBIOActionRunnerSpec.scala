package pl.touk.nussknacker.ui.process.repository

import cats.data.EitherT
import db.util.DBIOActionInstances._
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures}
import pl.touk.nussknacker.test.base.db.WithTestHsqlDb
import pl.touk.nussknacker.ui.db.NuTables
import pl.touk.nussknacker.ui.db.entity.ProcessEntityData

import java.sql.Timestamp
import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class DBIOActionRunnerSpec
    extends AnyFunSuiteLike
    with WithTestHsqlDb
    with PatientScalaFutures
    with Matchers
    with EitherValuesDetailedMessage {

  private lazy val profile = testDbRef.profile

  class SampleRepo extends NuTables {
    override protected val profile = DBIOActionRunnerSpec.this.profile

    import profile.api._

    def saveScenarioMetadata: EitherT[DB, Nothing, Int] = {
      EitherT.right(
        toEffectAll(
          processesTable += ProcessEntityData(
            id = ProcessId(-1L),
            name = ProcessName("fooScenario"),
            description = None,
            processCategory = "fooCategory",
            processingType = "fooProcessingType",
            isFragment = false,
            isArchived = false,
            createdAt = Timestamp.from(Instant.ofEpochMilli(0)),
            createdBy = "fooUser"
          )
        )
      )
    }

    def getScenarioMetadata: EitherT[DB, Nothing, Option[ProcessEntityData]] = {
      EitherT.right(
        toEffectAll(
          processesTable.filter(_.name === ProcessName("fooScenario")).take(1).result.headOption
        )
      )
    }

  }

  private lazy val repo = new SampleRepo

  test("exception should rollback transaction") {
    val runner = new DBIOActionRunner(testDbRef)
    runner
      .runInTransactionE[Nothing, Unit](for {
        _ <- repo.saveScenarioMetadata
        _ = {
          throw new SampleException
        }
      } yield ())
      .value
      .recover { case _: SampleException =>
        ()
      }
      .futureValue

    val result = runner
      .runInTransactionE[Nothing, Option[ProcessEntityData]](repo.getScenarioMetadata)
      .value
      .futureValue
      .rightValue

    result shouldBe empty
  }

  test("Left should rollback transaction") {
    val runner = new DBIOActionRunner(testDbRef)
    runner
      .runInTransactionE[String, Unit](for {
        _ <- repo.saveScenarioMetadata
        _ <- EitherT.leftT[DB, String]("break")
      } yield ())
      .value
      .futureValue

    val result = runner
      .runInTransactionE[Nothing, Option[ProcessEntityData]](repo.getScenarioMetadata)
      .value
      .futureValue
      .rightValue

    result shouldBe empty
  }

  class SampleException extends Exception

}
