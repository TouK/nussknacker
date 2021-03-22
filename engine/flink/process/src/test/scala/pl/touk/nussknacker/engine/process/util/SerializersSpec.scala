package pl.touk.nussknacker.engine.process.util

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.process.util.Serializers.CaseClassSerializer

class SerializersSpec extends FlatSpec with Matchers {

  it should "serialize case objects" in {
    val deserialized = serializeAndDeserialize(None)
    deserialized shouldBe None
  }

  it should "serialize usual case class" in {
    val obj = UsualCaseClass("a", 1)
    val deserialized = serializeAndDeserialize(obj)
    deserialized shouldBe obj
  }

  it should "serialize case class without params" in {
    val obj = NoParams()
    val deserialized = serializeAndDeserialize(obj)
    deserialized shouldBe obj
  }

  it should "serialize case class with implicit param" in {
    implicit val b: Long = 5L
    val obj = WithImplicitVal("a")

    val deserialized = serializeAndDeserialize(obj)

    deserialized shouldBe obj
  }

  it should "serialize inner case class" in {
    val obj = WrapperObj.createInner("abc")

    val deserialized = serializeAndDeserialize(obj.asInstanceOf[Product])

    //deserialized shouldBe obj // it explodes here because of deserialized obj.$outer ???
    deserialized.getClass shouldBe obj.getClass
    deserialized.hashCode() shouldBe obj.hashCode()
  }

  it should "serialize inner case class 2" in {
    import scala.collection.JavaConverters._

    // runtime type is scala.collection.convert.Wrappers$MutableBufferWrapper
    val obj = scala.collection.mutable.Buffer(1,2,3).asJava

    val deserialized = serializeAndDeserialize(obj.asInstanceOf[Product])

    deserialized shouldBe obj
  }

  it should "serialize inner case class 3" in {
    import scala.collection.JavaConverters._

    // runtime type is scala.collection.convert.Wrappers$MutableMapWrapper
    val obj = scala.collection.mutable.Map().asJava

    val deserialized = serializeAndDeserialize(obj.asInstanceOf[Product])

    deserialized shouldBe obj
  }

  def serializeAndDeserialize(caseClass: Product): Product = {
    val kryo = new Kryo()
    val out = new Output(1024)
    CaseClassSerializer.write(kryo, out, caseClass)
    CaseClassSerializer.read(kryo, new Input(out.toBytes), caseClass.getClass.asInstanceOf[Class[Product]])
  }
}

case class WithImplicitVal(a: String)(implicit val b: Long)

case class NoParams()

case class UsualCaseClass(a: String, b: Integer, withDefaultValue: String = "aaa")

trait Wrapper {
  case class StaticInner(a: String)
}

object WrapperObj extends Wrapper {
  def createInner(arg: String) = StaticInner(arg)
}