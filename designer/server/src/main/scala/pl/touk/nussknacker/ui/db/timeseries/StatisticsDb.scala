package pl.touk.nussknacker.ui.db.timeseries

import cats.effect.{IO, Resource}

import scala.concurrent.Future
import scala.language.higherKinds

trait StatisticsDb[F[_]] extends AutoCloseable {
  def initialize(): Unit
  def write(statistics: Map[String, Long]): F[Unit]
  def read(): F[Map[String, String]]
}

object StatisticsDb {

  def create(): Resource[IO, StatisticsDb[Future]] =
    Resource.make(
      acquire = for {
        db <- IO(QuestDb.create())
        _  <- IO(db.initialize())
      } yield db
    )(release = db => IO(db.close()))

}
