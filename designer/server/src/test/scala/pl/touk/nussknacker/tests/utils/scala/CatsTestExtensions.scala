package pl.touk.nussknacker.tests.utils.scala

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}

trait CatsTestExtensions {

  implicit class IOBasedResourceRun[T](val resource: Resource[IO, T]) {

    def run(): T = {
      resource.use(value => IO.pure(value)).unsafeRunSync()
    }

  }

  def eval[T](resource: Resource[IO, T]): T = resource.run()
}

object CatsTestExtensions extends CatsTestExtensions
