package pl.touk.esp.engine.api

import scala.concurrent.{ExecutionContext, Future}

trait Service {

  def open()(implicit ec: ExecutionContext): Unit = {

  }

  def close(): Unit = {

  }

}