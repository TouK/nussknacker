package pl.touk.nussknacker.test

import org.scalatest.time.{Millis, Minutes, Span}

trait ExtremelyPatientScalaFutures extends BasePatientScalaFutures {

  final override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Minutes)), interval = scaled(Span(100, Millis)))

}
