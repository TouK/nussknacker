package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import cats.data.Validated
import cats.data.Validated.Valid
import com.clearspring.analytics.stream.cardinality.{HyperLogLogPlus, ICardinality}
import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.{Kryo, KryoSerializable}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}

//Approximate unique count (using https://en.wikipedia.org/wiki/HyperLogLog). Result is number
case class HyperLogLogPlusAggregator(p: Int = 5, sp: Int = 10)
  extends CardinalityAggregator(() => new HyperLogLogPlus(p, sp), ic => new CardinalityWrapper(ic) {
    override def readFromBytes(bytes: Array[Byte]): ICardinality = HyperLogLogPlus.Builder.build(bytes)
  })

class CardinalityAggregator(zeroCardinality: () => ICardinality, wrapper: ICardinality => CardinalityWrapper) extends Aggregator with Serializable {

  override type Aggregate = CardinalityWrapper

  override type Element = AnyRef

  override def zero: CardinalityWrapper = wrapper(zeroCardinality())

  override def addElement(el: AnyRef, hll: Aggregate):Aggregate  = {
    val newOne = zeroCardinality()
    newOne.offer(el)
    wrapper(newOne.merge(hll.wrapped))
  }

  override def mergeAggregates(h1: CardinalityWrapper, h2: CardinalityWrapper): CardinalityWrapper = {
    wrapper(h1.wrapped.merge(h2.wrapped))
  }

  override def result(r: CardinalityWrapper): java.lang.Long = r.wrapped.cardinality()

  override def computeOutputType(input: TypingResult): Validated[String, TypingResult] = Valid(Typed[Long])

  override def computeStoredType(input: TypingResult): Validated[String, TypingResult] = Valid(Typed[CardinalityWrapper])

}

private[aggregate] abstract class CardinalityWrapper extends KryoSerializable with Serializable {

  def readFromBytes(bytes: Array[Byte]): ICardinality

  var wrapped: ICardinality = _

  def this(cardinality: ICardinality) = {
    this()
    this.wrapped = cardinality
  }

  override def write(kryo: Kryo, output: Output): Unit = {
    val bytes = wrapped.getBytes
    output.writeInt(bytes.length)
    output.writeBytes(wrapped.getBytes)
  }

  override def read(kryo: Kryo, input: Input): Unit = {
    val size = input.readInt()
    wrapped = readFromBytes(input.readBytes(size))
  }
}

