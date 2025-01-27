package pl.touk.nussknacker.engine.migration

import cats.data.NonEmptyList
import cats.syntax.functor._
import pl.touk.nussknacker.engine.migration.ProcessMigrations.CombineError.OverlappingMigrations
import pl.touk.nussknacker.engine.migration.ProcessMigrations.MigrationNumber

object ProcessMigrations {

  type MigrationNumber = Int

  def empty: ProcessMigrations = new ProcessMigrations {
    override def processMigrations: Map[MigrationNumber, ProcessMigration] = Map()
  }

  def combine(processMigrationsList: List[ProcessMigrations]): Either[CombineError, ProcessMigrations] = {
    processMigrationsList match {
      case Nil        => Right(empty)
      case one :: Nil => Right(one)
      case many       => combineAtLeastTwoListOfMigrations(many)
    }
  }

  private def combineAtLeastTwoListOfMigrations(processMigrationsList: List[ProcessMigrations]) = {
    val migrationNumberToOrigins: Map[MigrationNumber, List[ProcessMigrations]] =
      processMigrationsList
        .flatMap(pm => pm.processMigrations.keys.toList.tupleRight(pm))
        .groupBy(_._1)
        .map { case (migrationNumber, migrationNumberAndOriginList) =>
          migrationNumber -> migrationNumberAndOriginList.map(_._2)
        }
    val overlappingMigrationNumbers = migrationNumberToOrigins.filter { case (_, origins) => origins.size >= 2 }
    if (overlappingMigrationNumbers.nonEmpty) {
      Left(OverlappingMigrations(overlappingMigrationNumbers))
    } else {
      val combined = processMigrationsList
        .map(_.processMigrations)
        .reduceLeft((combinedMigrations, migrations) => combinedMigrations ++ migrations)
      Right(new ProcessMigrations {
        override def processMigrations: Map[MigrationNumber, ProcessMigration] = combined
      })
    }
  }

  sealed trait CombineError

  object CombineError {
    // To allow overlapping numbers, we would need to version model independently for each list of process migrations.
    // That is "model_version" column should be replaced with a column or a table that contains version for each migrations list.
    case class OverlappingMigrations(migrationNumberToOrigins: Map[MigrationNumber, List[ProcessMigrations]])
        extends CombineError
  }

}

trait ProcessMigrations extends Serializable {

  def processMigrations: Map[MigrationNumber, ProcessMigration]

  // we assume 0 is minimal version
  def version: MigrationNumber = (processMigrations.keys.toSet + 0).max

}
