//package org.apache.flink.runtime.types
//
//import com.esotericsoftware.kryo.io.{Input, Output}
//import org.scalatest.flatspec.AnyFlatSpec
//import org.scalatest.matchers.should.Matchers
//
//import scala.collection.mutable
//import scala.jdk.CollectionConverters._
//
//class FlinkScalaKryoInstantiatorSpec extends AnyFlatSpec with Matchers {
//
//  it should "serialize and deserialize records properly" in {
//    val kryo = new FlinkScalaKryoInstantiator().newKryo
//
//    val record = Record(true, 5, "abc",
//      Map("a" -> 1, "b" -> 2), List("123", "abc"), Set("abc"),
//      mutable.Map("a" -> 123).asJava, mutable.Buffer("abc").asJava, mutable.Set("abc").asJava)
//    val output = new Output(1024)
//    kryo.writeClassAndObject(output, record)
//    val input = new Input(output.toBytes)
//    val result = kryo.readClassAndObject(input)
//
//    result shouldBe record
//  }
//}
//
//case class Record(boolean: Boolean, int: Int, string: String,
//                  map: Map[String, Int], list: List[String], set: Set[String],
//                  javaMap: java.util.Map[String, Int], javaList: java.util.List[String], javaSet: java.util.Set[String])
