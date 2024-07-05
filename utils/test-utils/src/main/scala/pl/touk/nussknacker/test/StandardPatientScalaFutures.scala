package pl.touk.nussknacker.test

import org.scalatest.time.{Millis, Seconds, Span}

trait StandardPatientScalaFutures extends BasePatientScalaFutures {

  final override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(100, Millis)))

}
