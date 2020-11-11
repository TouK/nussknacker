package pl.touk.nussknacker.engine.benchmarks.avro

import org.scalatest.{FunSuite, Matchers}

class AvroBenchmarkSpec extends FunSuite with Matchers {

  test("serialization round trips are correct") {
    val benchmark = new AvroBenchmark
    benchmark.defaultFlinkKryoSetup.roundTripSerialization() shouldEqual AvroSamples.sampleRecord
    benchmark.defaultFlinkAvroSetup.roundTripSerialization() shouldEqual AvroSamples.sampleRecord
  }

}
