package pl.touk.esp.engine.api

import scala.concurrent.ExecutionContext

trait Lifecycle {

  def open()(implicit ec: ExecutionContext): Unit = {

  }

  def close(): Unit = {

  }

}
