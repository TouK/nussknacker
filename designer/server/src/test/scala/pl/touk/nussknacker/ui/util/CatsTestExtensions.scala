package pl.touk.nussknacker.ui.util

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global

object CatsTestExtensions {

  implicit class IOBasedResourceRun[T](val resource: Resource[IO, T]) extends AnyVal {
    def run(): T = {
      resource.use(value => IO.pure(value)).unsafeRunSync()
    }
  }

  def eval[T](resource: Resource[IO, T]): T = resource.run()
}
