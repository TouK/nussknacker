package pl.touk.nussknacker.test.utils.scalas

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}

object CatsTestExtensions {

  implicit class IOBasedResourceRun[T](val resource: Resource[IO, T]) extends AnyVal {

    def run(): T = {
      resource.use(value => IO.pure(value)).unsafeRunSync()
    }

  }

  def eval[T](resource: Resource[IO, T]): T = resource.run()
}
