package pl.touk.nussknacker.test.utils.scalas

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global

trait CatsTestExtensions {

  implicit class IOBasedResourceRun[T](val resource: Resource[IO, T]) {

    def run(): T = {
      resource.use(value => IO.pure(value)).unsafeRunSync()
    }

  }

  def eval[T](resource: Resource[IO, T]): T = resource.run()
}

object CatsTestExtensions extends CatsTestExtensions
