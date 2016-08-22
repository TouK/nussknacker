package pl.touk.esp.engine.process

import pl.touk.esp.engine.api.Service
import pl.touk.esp.engine.compile.{PartSubGraphCompiler, ProcessCompiler}

import scala.concurrent.ExecutionContext

class ServicesLifecycle(services: Seq[Service]) {
  def open()(implicit ec: ExecutionContext) = {
    services.foreach(_.open()) // TODO: shouldn't we wait on it?
  }

  def close() = {
    services.foreach(_.close()) // TODO: shouldn't we wait on it?
  }
}

case class PartSubGraphCompilerWithServicesLifecycle(compiler: PartSubGraphCompiler, servicesLifecycle: ServicesLifecycle)
