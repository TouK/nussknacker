package pl.touk.nussknacker.engine.process.registrar

import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}

import scala.concurrent.ExecutionContext

object IORuntimeFactory {

  def create(ec: ExecutionContext): IORuntime = {
    val (scheduler, shutdown) = Scheduler.createDefaultScheduler()
    IORuntime(ec, ec, scheduler, shutdown, IORuntimeConfig())
  }

}
