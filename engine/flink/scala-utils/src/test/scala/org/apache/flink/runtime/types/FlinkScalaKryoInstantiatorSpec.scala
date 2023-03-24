package org.apache.flink.runtime.types
import com.esotericsoftware.kryo.io.{Input, Output}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.jdk.CollectionConverters._

class FlinkScalaKryoInstantiatorSpec extends AnyFlatSpec with Matchers {

  it should "serialize and deserialize records properly" in {
    val kryo = new FlinkScalaKryoInstantiator().newKryo

    val record = Record(5, Map("a"->1, "b"->2), List("123", "abc"), mutable.Map("a"-> 123).asJava)
    val output = new Output(1024)
    kryo.writeClassAndObject(output, record)
    val input = new Input(output.toBytes)
    val result = kryo.readClassAndObject(input)

    result shouldBe record
  }
}

case class Record(int: Int, map: Map[String, Int], list: List[String], javaMap: java.util.Map[String, Int])
