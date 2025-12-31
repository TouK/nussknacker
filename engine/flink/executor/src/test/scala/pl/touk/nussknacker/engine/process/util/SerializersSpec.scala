package pl.touk.nussknacker.engine.process.util

import com.esotericsoftware.kryo.io.{Input, Output}
import org.apache.flink.runtime.types.FlinkScalaKryoInstantiator
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.{
  Duration,
  Instant,
  LocalDate,
  LocalDateTime,
  LocalTime,
  MonthDay,
  OffsetDateTime,
  OffsetTime,
  Period,
  Year,
  YearMonth,
  ZonedDateTime,
  ZoneId,
  ZoneOffset
}
import java.util.Collections
import scala.jdk.CollectionConverters._

class SerializersSpec extends AnyFlatSpec with Matchers {

  it should "serialize case objects" in {
    checkSerializeDeserializeRoundTrip(None)
  }

  it should "serialize usual case class" in {
    val obj = UsualCaseClass("a", 1)
    checkSerializeDeserializeRoundTrip(obj)
  }

  it should "serialize case class without params" in {
    val obj = NoParams()
    checkSerializeDeserializeRoundTrip(obj)
  }

  it should "serialize case class with implicit param" in {
    implicit val b: Long = 5L
    val obj              = WithImplicitVal("a")

    checkSerializeDeserializeRoundTrip(obj)
  }

  it should "serialize inner case class" in {
    val obj = WrapperObj.createInner("abc")

    val deserialized = checkSerializeDeserializeRoundTrip(obj)

    // deserialized shouldBe obj // it explodes here because of deserialized obj.$outer ???
    deserialized.getClass shouldBe obj.getClass
    deserialized.hashCode() shouldBe obj.hashCode()
  }

  it should "serialize inner case class 2" in {
    val obj = WrapperObj.InnerGenericCaseClass("a")

    checkSerializeDeserializeRoundTrip(obj)
  }

  it should "serialize inner case class 3" in {
    val obj = WrapperObj.InnerGenericCaseClassWithTrait[String]("a")

    checkSerializeDeserializeRoundTrip(obj)
  }

  it should "serialize case class with serial version uid" in {
    val obj = CaseClassWithSerialVersionUID("a")
    checkSerializeDeserializeRoundTrip(obj)
  }

  it should "serialize nested case class with serial version uid" in {
    val obj          = ObjCaseClassWrapper.CaseClassWithSerialVersionUIDInObject("a")
    val deserialized = checkSerializeDeserializeRoundTrip(obj)
    deserialized.a shouldBe obj.a
    deserialized.someField shouldBe obj.someField
  }

  it should "serialize unmodifiableList" in {
    val obj = Collections.unmodifiableList(List("foo", "bar").asJava)
    checkSerializeDeserializeRoundTrip(obj)
  }

  it should "serialize unmodifiableMap" in {
    val obj = Collections.unmodifiableMap(Map("foo" -> 1, "bar" -> 2).asJava)
    checkSerializeDeserializeRoundTrip(obj)
  }

  it should "serialize date/time classes" in {
    checkSerializeDeserializeRoundTrip(Duration.ofMillis(123))
    checkSerializeDeserializeRoundTrip(Instant.ofEpochMilli(123))
    checkSerializeDeserializeRoundTrip(LocalDate.of(2026, 1, 1))
    checkSerializeDeserializeRoundTrip(LocalTime.of(12, 13))
    checkSerializeDeserializeRoundTrip(LocalDateTime.of(2026, 1, 1, 12, 13))
    checkSerializeDeserializeRoundTrip(ZoneOffset.of("+01:00"))
    checkSerializeDeserializeRoundTrip(ZoneId.of("GMT"))
    checkSerializeDeserializeRoundTrip(OffsetTime.of(LocalTime.of(12, 13), ZoneOffset.of("+01:00")))
    checkSerializeDeserializeRoundTrip(
      OffsetDateTime.of(LocalDate.of(2026, 1, 1), LocalTime.of(12, 13), ZoneOffset.of("+01:00"))
    )
    checkSerializeDeserializeRoundTrip(
      ZonedDateTime.of(LocalDate.of(2026, 1, 1), LocalTime.of(12, 13), ZoneId.of("GMT"))
    )
    checkSerializeDeserializeRoundTrip(Year.of(2026))
    checkSerializeDeserializeRoundTrip(YearMonth.of(2026, 1))
    checkSerializeDeserializeRoundTrip(MonthDay.of(1, 2))
    checkSerializeDeserializeRoundTrip(Period.ofWeeks(12))
  }

  def checkSerializeDeserializeRoundTrip[T](obj: T): T = {
    val kryo = new FlinkScalaKryoInstantiator().newKryo()
    val out  = new Output(1024)
    kryo.writeObject(out, obj)
    val deserialized = kryo.readObject(new Input(out.toBytes), obj.getClass.asInstanceOf[Class[T]])
    deserialized shouldBe obj
    deserialized
  }

}

case class WithImplicitVal(a: String)(implicit val b: Long)

case class NoParams()

case class UsualCaseClass(a: String, b: Integer, withDefaultValue: String = "aaa")

@SerialVersionUID(-3277343097189933789L)
case class CaseClassWithSerialVersionUID(a: String)

trait CaseClassWrapper {

  @SerialVersionUID(-2277343097189933789L)
  case class CaseClassWithSerialVersionUIDInObject(a: String, foo: String = "abc") {
    val someField: String = a
  }

}

@SerialVersionUID(-1277343097189933789L)
object ObjCaseClassWrapper extends CaseClassWrapper

trait Wrapper {
  case class StaticInner(a: String)

  trait SomeTrait[T] {
    def get2: T
  }

  abstract class Abstract[T](value: T) {
    def get1: T = value

    override def equals(obj: Any): Boolean =
      obj != null && obj.isInstanceOf[Abstract[_]] && obj.asInstanceOf[Abstract[_]].get1 == value
  }

  case class InnerGenericCaseClass[A](value: A) extends Abstract[A](value)

  case class InnerGenericCaseClassWithTrait[A](value: A) extends Abstract[A](value) with SomeTrait[A] {
    override def get2: A = value
  }

}

object WrapperObj extends Wrapper {
  def createInner(arg: String) = StaticInner(arg)
}
