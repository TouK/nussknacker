package pl.touk.nussknacker.engine.migration

import org.scalatest.Inside.inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.test.EitherValuesDetailedMessage

class ProcessMigrationsTest extends AnyFunSuite with Matchers with EitherValuesDetailedMessage {

  test("should combine non overlapping migrations") {
    val migrations1 = createMigrations(100, 200)
    val migrations2 = createMigrations(101, 102, 103)
    val migrations3 = createMigrations(110, 120)

    val combined = ProcessMigrations.combine(migrations1 :: migrations2 :: migrations3 :: Nil)

    combined.rightValue.processMigrations.keySet should contain only (100, 101, 102, 103, 110, 120, 200)
  }

  test("should combine single migrations list") {
    val migrations = createMigrations(100, 200)

    val combined = ProcessMigrations.combine(migrations :: Nil)

    combined.rightValue shouldBe theSameInstanceAs(migrations)
  }

  test("should combine empty migrations list") {
    val combined = ProcessMigrations.combine(Nil)

    combined.rightValue.version shouldBe 0
    combined.rightValue.processMigrations shouldBe Symbol("empty")
  }

  test("should return error with overlapping migrations") {
    val migrations1 = createMigrations(100, 200, 300, 400, 500)
    val migrations2 = createMigrations(101, 200, 201, 202, 203, 300, 301)
    val migrations3 = createMigrations(200, 210)

    val combined = ProcessMigrations.combine(migrations1 :: migrations2 :: migrations3 :: Nil)

    inside(combined.leftValue) { case ProcessMigrations.CombineError.OverlappingMigrations(overlappingMigrations) =>
      overlappingMigrations.keySet should contain only (200, 300)
      overlappingMigrations(200) should contain only (migrations1, migrations2, migrations3)
      overlappingMigrations(300) should contain only (migrations1, migrations2)
    }
  }

  private def createMigrations(migrationIds: Int*): ProcessMigrations = {
    new ProcessMigrations {
      override def processMigrations: Map[Int, ProcessMigration] = {
        migrationIds.map(id => id -> EmptyMigration).toMap
      }
    }
  }

  private object EmptyMigration extends ProcessMigration {
    override def description: String = ???

    override def migrateProcess(canonicalProcess: CanonicalProcess, category: String): CanonicalProcess = ???
  }

}
