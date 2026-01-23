package pl.touk.nussknacker.engine.process.util

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.process.util.IoUtils._

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration._

class IOUtilsTest extends AnyFunSuite with Matchers {

  test("unsafeRunTimedAttempt should return value") {
    implicit val ioRuntime: IORuntime = IORuntime.global
    val timeout                       = 2 seconds
    val testIo                        = IO.sleep(timeout.div(2)).map(_ => "result")

    testIo.unsafeRunTimedAttempt(timeout) shouldBe Right("result")
  }

  test("unsafeRunTimedAttempt should return timeout error") {
    implicit val ioRuntime: IORuntime = IORuntime.global
    val timeout                       = 2 seconds
    val testIo                        = IO.never

    testIo.unsafeRunTimedAttempt(timeout) shouldBe Left(Error.Timeout)
  }

  test("unsafeRunTimedAttempt should return interrupted error") {
    implicit val ioRuntime: IORuntime = IORuntime.global
    val timeout                       = 10.seconds
    val resultRef                     = new AtomicReference[Either[Error, Unit]]()
    val testIo                        = IO.never

    val thread = new Thread(() => {
      val result = testIo.unsafeRunTimedAttempt(timeout)
      resultRef.set(result)
    })

    thread.start()

    Thread.sleep(100)

    thread.interrupt()
    thread.join()

    resultRef.get() shouldBe Left(Error.Interrupted)
  }

}
