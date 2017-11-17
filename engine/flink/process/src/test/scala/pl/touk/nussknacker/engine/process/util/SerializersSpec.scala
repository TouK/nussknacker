package pl.touk.nussknacker.engine.process.util

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.process.util.Serializers.CaseClassSerializer

class SerializersSpec extends FlatSpec with Matchers {

  it should "serialize case objects" in {
    val kryo = new Kryo()
    val out = new Output(1024)

    CaseClassSerializer.write(kryo, out, None)

    val deserialized = CaseClassSerializer.read(kryo, new Input(out.toBytes), None.getClass.asInstanceOf[Class[Product]])

    deserialized shouldBe None
  }

}
