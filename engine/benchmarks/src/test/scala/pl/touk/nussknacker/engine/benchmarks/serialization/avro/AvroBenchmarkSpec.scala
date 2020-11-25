package pl.touk.nussknacker.engine.benchmarks.serialization.avro

import org.scalatest.{FunSuite, Matchers}

class AvroBenchmarkSpec extends FunSuite with Matchers {

  test("serialization round trips are correct") {
    val benchmark = new AvroBenchmark
    benchmark.defaultFlinkKryoSetup.roundTripSerialization()._1 shouldEqual AvroSamples.sampleRecord
    benchmark.defaultFlinkAvroSetup.roundTripSerialization()._1 shouldEqual AvroSamples.sampleRecord
    benchmark.schemaIdBasedKryoSetup.roundTripSerialization()._1 shouldEqual AvroSamples.sampleRecordWithSchemaId
    benchmark.flinkAvroWithSchemaIdSetup.roundTripSerialization()._1 shouldEqual AvroSamples.sampleRecordWithSchemaId
  }

}
