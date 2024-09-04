package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import cats.data.Validated.Valid
import com.github.ghik.silencer.silent
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.api.java.typeutils.runtime.ValueSerializer
import org.apache.flink.api.java.typeutils.runtime.kryo.KryoSerializer
import org.apache.flink.core.memory.{DataInputViewStreamWrapper, DataOutputViewStreamWrapper}
import org.scalatest.Assertion
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.util.Random

@silent("deprecated")
class HyperLogLogPlusAggregatorSpec extends AnyFunSuite with Matchers {

  // the aim of this test is to be able to test different parameters easily
  test("run alg for different params") {

    val set1 = generateRandomData(unique = 20, randomRepeat = 5)
    val set2 = generateRandomData(unique = 100, randomRepeat = 20)
    val set3 = generateRandomData(unique = 1000, randomRepeat = 30)

    val lowerPrecision = HyperLogLogPlusAggregator(5, 10)
    runForData(lowerPrecision, set1) shouldBe (19, 33)
    runForData(lowerPrecision, set2) shouldBe (86, 32)
    runForData(lowerPrecision, set3) shouldBe (1476, 32)

    val higherPrecision = HyperLogLogPlusAggregator(10, 15)
    runForData(higherPrecision, set1) shouldBe (20, 47)
    runForData(higherPrecision, set2) shouldBe (100, 200)
    runForData(higherPrecision, set3) shouldBe (1033, 693)

  }

  private def generateRandomData(unique: Int, randomRepeat: Int): Seq[String] = {
    // shuffle and number of repeats should not make difference to result
    // we use string as in many cases we count unique string keys, but this shouldn't matter much
    Random.shuffle(
      (1 to unique)
        .flatMap(k => (0 to Random.nextInt(randomRepeat)).map(_ => k.toString))
    )
  }

  private def runForData(agg: HyperLogLogPlusAggregator, data: Iterable[AnyRef]): (Long, Int) = {
    val total        = data.foldRight(agg.zero)(agg.addElement)
    val uniqueCounts = agg.result(total)
    val usedMemory   = total.wrapped.getBytes.length
    (uniqueCounts, usedMemory)
  }

  test("Serialize correctly both with Kryo and native Flink") {
    val ex = new ExecutionConfig

    val hll = HyperLogLogPlusAggregator()

    val ic = hll.zero
    (1 to 10).foreach(i => ic.wrapped.offer(i.toString))

    val storedType = Typed.typedClass[HyperLogLogPlusWrapper]
    hll.computeStoredType(Unknown) shouldBe Valid(storedType)

    def checkSerialization(
        rawTypeInfo: TypeInformation[_],
        bytesOverHead: Int,
        serializerAssertion: TypeSerializer[_] => Assertion
    ) = {
      val typeInfo   = rawTypeInfo.asInstanceOf[TypeInformation[CardinalityWrapper]]
      val serializer = typeInfo.createSerializer(ex)

      val compatibility = serializer
        .snapshotConfiguration()
        .resolveSchemaCompatibility(typeInfo.createSerializer(ex))
      compatibility.isCompatibleAsIs shouldBe true

      val data = new ByteArrayOutputStream(1024)
      serializer.asInstanceOf[TypeSerializer[CardinalityWrapper]].serialize(ic, new DataOutputViewStreamWrapper(data))
      serializerAssertion(serializer)

      // We check expected serialized size to avoid surprises when e.g. sth causes switching back to Kryo/Pojo serialization...
      data.size() shouldBe ic.wrapped.getBytes.length + bytesOverHead

      val bytes = serializer.deserialize(new DataInputViewStreamWrapper(new ByteArrayInputStream(data.toByteArray)))
      bytes.wrapped shouldBe ic.wrapped

    }

    val typedTypeInfo = TypeInformationDetection.instance.forType(storedType)

    /*
      Checks below assert that our serialization remains efficient. bytesOverHead value comes from KryoSerializable and Value implementations in CardinalityWrapper
      Assertions below will fail if either serialization method will be changed, or when (e.g. after Flink upgrade) Flink will no longer use our serialization methods
     */
    checkSerialization(
      typedTypeInfo.asInstanceOf[TypeInformation[CardinalityWrapper]],
      4,
      _ shouldBe new ValueSerializer(classOf[HyperLogLogPlusWrapper])
    )
    checkSerialization(
      TypeInformation.of(storedType.klass),
      4,
      _ shouldBe new ValueSerializer(classOf[HyperLogLogPlusWrapper])
    )
    // this is Kryo overhead, can change with Flink/Kryo version
    checkSerialization(TypeInformation.of(classOf[Any]), 91, _ shouldBe new KryoSerializer(classOf[Any], ex))
  }

}
