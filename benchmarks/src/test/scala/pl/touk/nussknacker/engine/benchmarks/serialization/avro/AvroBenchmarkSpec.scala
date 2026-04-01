package pl.touk.nussknacker.engine.benchmarks.serialization.avro

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class AvroBenchmarkSpec extends AnyFunSuite with Matchers {

  test("serialization round trips are correct") {
    val benchmark = new AvroBenchmark
    benchmark.defaultFlinkKryoSetup.roundTripSerialization()._1 shouldEqual AvroSamples.sampleRecord
    benchmark.schemaIdBasedKryoSetup.roundTripSerialization()._1 shouldEqual AvroSamples.sampleRecordWithSchemaId
  }

}
