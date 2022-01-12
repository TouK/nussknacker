package pl.touk.nussknacker.test

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}

trait VeryPatientScalaFutures extends ScalaFutures with Eventually {

  final override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(120, Seconds)), interval = scaled(Span(100, Millis)))

}
