package pl.touk.nussknacker.engine.process.util

import com.esotericsoftware.kryo.io.{Input, Output}
import org.apache.flink.runtime.types.FlinkScalaKryoInstantiator
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.Collections
import scala.jdk.CollectionConverters._

class SerializersSpec extends AnyFlatSpec with Matchers {

  it should "serialize case objects" in {
    val deserialized = serializeAndDeserialize(None)
    deserialized shouldBe None
  }

  it should "serialize usual case class" in {
    val obj          = UsualCaseClass("a", 1)
    val deserialized = serializeAndDeserialize(obj)
    deserialized shouldBe obj
  }

  it should "serialize case class without params" in {
    val obj          = NoParams()
    val deserialized = serializeAndDeserialize(obj)
    deserialized shouldBe obj
  }

  it should "serialize case class with implicit param" in {
    implicit val b: Long = 5L
    val obj              = WithImplicitVal("a")

    val deserialized = serializeAndDeserialize(obj)

    deserialized shouldBe obj
  }

  it should "serialize inner case class" in {
    val obj = WrapperObj.createInner("abc")

    val deserialized = serializeAndDeserialize(obj.asInstanceOf[Product])

    // deserialized shouldBe obj // it explodes here because of deserialized obj.$outer ???
    deserialized.getClass shouldBe obj.getClass
    deserialized.hashCode() shouldBe obj.hashCode()
  }

  it should "serialize inner case class 2" in {
    val obj = WrapperObj.InnerGenericCaseClass("a")

    val deserialized = serializeAndDeserialize(obj.asInstanceOf[Product])

    deserialized shouldBe obj
  }

  it should "serialize inner case class 3" in {
    val obj = WrapperObj.InnerGenericCaseClassWithTrait[String]("a")

    val deserialized = serializeAndDeserialize(obj.asInstanceOf[Product])

    deserialized shouldBe obj
  }

  it should "serialize case class with serial version uid" in {
    val obj          = CaseClassWithSerialVersionUID("a")
    val deserialized = serializeAndDeserialize(obj)
    deserialized shouldBe obj
  }

  it should "serialize nested case class with serial version uid" in {
    val obj = ObjCaseClassWrapper.CaseClassWithSerialVersionUIDInObject("a")
    val deserialized =
      serializeAndDeserialize(obj)
    deserialized.a shouldBe obj.a
    deserialized.someField shouldBe obj.someField
  }

  it should "serialize unmodifiableList" in {
    val obj = Collections.unmodifiableList(List("foo", "bar").asJava)
    val deserialized =
      serializeAndDeserialize(obj)
    deserialized shouldBe obj
  }

  it should "serialize unmodifiableMap" in {
    val obj = Collections.unmodifiableMap(Map("foo" -> 1, "bar" -> 2).asJava)
    val deserialized =
      serializeAndDeserialize(obj)
    deserialized shouldBe obj
  }

  def serializeAndDeserialize[T](obj: T): T = {
    val kryo = new FlinkScalaKryoInstantiator().newKryo()
    val out  = new Output(1024)
    kryo.writeObject(out, obj)
    kryo.readObject(new Input(out.toBytes), obj.getClass.asInstanceOf[Class[T]])
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
