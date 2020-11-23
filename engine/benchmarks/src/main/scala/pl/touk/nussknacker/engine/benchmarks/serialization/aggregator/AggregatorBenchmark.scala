package pl.touk.nussknacker.engine.benchmarks.serialization.aggregator

import java.util.concurrent.TimeUnit

import org.apache.flink.api.common.typeinfo.{BasicTypeInfo, TypeHint, TypeInfo, TypeInformation}
import org.apache.flink.api.java.typeutils.{ListTypeInfo, MapTypeInfo}
import org.openjdk.jmh.annotations.{Benchmark, BenchmarkMode, Mode, OutputTimeUnit, Scope, State}
import pl.touk.nussknacker.engine.benchmarks.serialization.SerializationBenchmarkSetup
import org.apache.flink.api.scala._

import scala.collection.JavaConverters._
import scala.collection.immutable.TreeMap

@State(Scope.Thread)
class AggregatorBenchmark {

  private def treeMap(content: (Long,AnyRef)*) = new SerializationBenchmarkSetup(
    _ => {}, TypeInformation.of(classOf[TreeMap[Long, AnyRef]]), TreeMap(content:_*)
  )

  private def treeMapOpt(content: (Long,AnyRef)*) = new SerializationBenchmarkSetup(
      _ => {}, treeMapTypeDef(implicitly[TypeInformation[Map[String, AnyRef]]]).asInstanceOf[TypeInformation[TreeMap[Long, AnyRef]]], TreeMap(content:_*)
  )

  private def treeMapTypeDef[T](implicit el: TypeInformation[T]) = {
    implicitly[TypeInformation[TreeMap[Long, T]]]
  }

  private def forMap(fieldNo: Int, bucketNo: Int) = treeMap(prepareMap(fieldNo, bucketNo):_*)

  private def prepareMap(fieldNo: Int, bucketNo: Int) = {
    (0 to bucketNo).toList
      .map(r => (r * 1000L, (0 to fieldNo).map(fn => s"volume$fn" -> r * fn.toLong).toMap))
  }

  private val manyBucketsOpt = new SerializationBenchmarkSetup(_ => {},
    new MapTypeInfo(BasicTypeInfo.LONG_TYPE_INFO, new ListTypeInfo(BasicTypeInfo.LONG_TYPE_INFO)),
    (1 to 100).map(_.toLong: java.lang.Long).map(k => k -> List[java.lang.Long](k, k*2, k*3, k*4).asJava).toMap.asJava
  )

  private val manyBucketsSomeOpt = new SerializationBenchmarkSetup(_ => {},
    new MapTypeInfo(BasicTypeInfo.LONG_TYPE_INFO, new MapTypeInfo(BasicTypeInfo.STRING_TYPE_INFO, BasicTypeInfo.LONG_TYPE_INFO)),
    (1 to 100).map(_.toLong: java.lang.Long)
      .map(k => k -> Map[String, java.lang.Long]("volume1" -> k, "volume1" -> k*2, "volume1" -> k*3, "volume1" -> k*4).asJava).toMap.asJava
  )

  private val manyBucketsOptKryoValues = new SerializationBenchmarkSetup(_ => {},
    new MapTypeInfo(BasicTypeInfo.LONG_TYPE_INFO, new MapTypeInfo(BasicTypeInfo.STRING_TYPE_INFO, TypeInformation.of(classOf[Any]))),
    (1 to 100).map(_.toLong: java.lang.Long)
      .map(k => k -> Map[String, Any]("volume1" -> k, "volume1" -> k*2, "volume1" -> k*3, "volume1" -> k*4).asJava).toMap.asJava
  )

  private val manyBucketsOptKryoScalaMapValues = new SerializationBenchmarkSetup(_ => {},
    new MapTypeInfo(BasicTypeInfo.LONG_TYPE_INFO, implicitly[TypeInformation[Map[String, Any]]]),
    (1 to 100).map(_.toLong: java.lang.Long)
      .map(k => k -> Map[String, Any]("volume1" -> k, "volume1" -> k*2, "volume1" -> k*3, "volume1" -> k*4)).toMap.asJava
  )

  private val optTreeMap = treeMapOpt(prepareMap(4, 100):_*)

  private val fewBuckets = forMap(4, 10)

  private val manyBuckets = forMap(4, 100)

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def optTreeMapRun(): AnyRef = {
    optTreeMap.roundTripSerialization()
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def manyBucketsSomeOptRun(): AnyRef = {
    manyBucketsSomeOpt.roundTripSerialization()
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def manyBucketsOptRun(): AnyRef = {
    manyBucketsOpt.roundTripSerialization()
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def manyBucketsOptKryoScalaMapValuesRun(): AnyRef = {
    manyBucketsOptKryoScalaMapValues.roundTripSerialization()
  }


  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def manyBucketsOptKryoValuesRun(): AnyRef = {
    manyBucketsOptKryoValues.roundTripSerialization()
  }


  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def deafultFewBuckets(): AnyRef = {
    fewBuckets.roundTripSerialization()
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def deafultManyBuckets(): AnyRef = {
    manyBuckets.roundTripSerialization()
  } 

}
