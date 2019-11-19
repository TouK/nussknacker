package pl.touk.nussknacker.test

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}

/**
 * Default ScalaFutures patient is set to timeout = 150ms and interval = 15ms. It is a good setting in the perfect scenario
 * when asynchronous tasks are very short and global ExecutionContext works without delays. But ... in the real World we have
 * very vary tasks duration and on slow environments (like Travis) it cause occasional delays. So we need to be more patient.
 */
trait PatientScalaFutures extends ScalaFutures with Eventually {

  final override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(50, Millis)))

}
