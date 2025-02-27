package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import cats.data.Validated
import cats.data.Validated.Valid
import com.clearspring.analytics.stream.cardinality.{HyperLogLogPlus, ICardinality}
import com.esotericsoftware.kryo.{Kryo, KryoSerializable}
import com.esotericsoftware.kryo.io.{Input, Output}
import org.apache.flink.core.memory.{DataInputView, DataOutputView}
import org.apache.flink.types.Value
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}

import java.util.Objects
import scala.reflect.ClassTag

//Approximate unique count (using https://en.wikipedia.org/wiki/HyperLogLog). Result is number
//The parameters should be adjusted for use case, see HyperLogLogPlusAggregatorSpec for some experiments
case class HyperLogLogPlusAggregator(p: Int = 10, sp: Int = 15)
    extends CardinalityAggregator(() => new HyperLogLogPlus(p, sp), ic => new HyperLogLogPlusWrapper(ic))

class CardinalityAggregator[Wrapper <: CardinalityWrapper: ClassTag](
    zeroCardinality: () => ICardinality,
    wrapper: ICardinality => Wrapper
) extends Aggregator
    with Serializable {

  override type Aggregate = CardinalityWrapper

  override type Element = AnyRef

  override def zero: CardinalityWrapper = wrapper(zeroCardinality())

  override def addElement(el: AnyRef, hll: Aggregate): Aggregate = {
    val newOne = zeroCardinality()
    newOne.offer(el)
    wrapper(newOne.merge(hll.wrapped))
  }

  override def mergeAggregates(h1: CardinalityWrapper, h2: CardinalityWrapper): CardinalityWrapper = {
    wrapper(h1.wrapped.merge(h2.wrapped))
  }

  override def result(r: CardinalityWrapper): java.lang.Long = r.wrapped.cardinality()

  override def computeOutputType(input: TypingResult): Validated[String, TypingResult] = Valid(Typed[Long])

  override def computeStoredType(input: TypingResult): Validated[String, TypingResult] = Valid(Typed[Wrapper])

}

class HyperLogLogPlusWrapper extends CardinalityWrapper {

  def this(cardinality: ICardinality) = {
    this()
    this.wrapped = cardinality
  }

  override def readFromBytes(bytes: Array[Byte]): ICardinality = HyperLogLogPlus.Builder.build(bytes)

}

//We extend both Flink and Kryo serialization mechanisms, to serialize both when using generic types and types based on TypingResult
private[aggregate] abstract class CardinalityWrapper extends Value with KryoSerializable with Serializable {

  def readFromBytes(bytes: Array[Byte]): ICardinality

  var wrapped: ICardinality = _

  override def write(kryo: Kryo, output: Output): Unit = {
    val bytes = wrapped.getBytes
    output.writeInt(bytes.length)
    output.writeBytes(wrapped.getBytes)
  }

  override def read(kryo: Kryo, input: Input): Unit = {
    val size = input.readInt()
    wrapped = readFromBytes(input.readBytes(size))
  }

  override def write(out: DataOutputView): Unit = {
    val bytes = wrapped.getBytes
    out.writeInt(bytes.length)
    out.write(bytes)
  }

  override def read(in: DataInputView): Unit = {
    val size  = in.readInt()
    val bytes = new Array[Byte](size)
    in.read(bytes)
    wrapped = readFromBytes(bytes)
  }

  def canEqual(other: Any): Boolean = other.isInstanceOf[CardinalityWrapper]

  override def equals(other: Any): Boolean = other match {
    case that: CardinalityWrapper =>
      (that canEqual this) &&
      wrapped == that.wrapped
    case _ => false
  }

  override def hashCode(): Int = {
    Objects.hash(wrapped)
  }

}
