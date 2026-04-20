package pl.touk.nussknacker.engine.flink.api.typeinfo

import org.apache.flink.api.common.serialization.SerializerConfigImpl
import org.apache.flink.api.common.typeinfo.Types
import org.apache.flink.core.memory.{DataInputViewStreamWrapper, DataOutputViewStreamWrapper}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.{util => jutil}
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

class NullableListTypeInfoSpec extends AnyFunSuite with Matchers {

  test("list serialization handles null elements") {
    val list = new jutil.ArrayList[java.lang.Integer]()
    list.add(1)
    list.add(null)
    list.add(1)

    val typeInfo = new NullableListTypeInfo[java.lang.Integer](Types.INT)
    serializeRoundTrip(list, typeInfo) shouldEqual list
  }

  test("list of lists handles null inner list") {
    val innerList: jutil.List[String] = jutil.Arrays.asList("x", "y")
    val outerList                     = new jutil.ArrayList[jutil.List[String]]()
    outerList.add(innerList)
    outerList.add(null)
    outerList.add(innerList)

    val innerTypeInfo = new NullableListTypeInfo[String](Types.STRING)
    val outerTypeInfo = new NullableListTypeInfo[jutil.List[String]](innerTypeInfo)
    serializeRoundTrip(outerList, outerTypeInfo) shouldEqual outerList
  }

  test("list serialization handles null list") {
    val typeInfo                                = new NullableListTypeInfo[java.lang.Integer](Types.INT)
    val nullList: jutil.List[java.lang.Integer] = null
    serializeRoundTrip(nullList, typeInfo) shouldBe null
  }

  private def serializeRoundTrip[T](value: jutil.List[T], typeInfo: NullableListTypeInfo[T]): jutil.List[T] = {
    val serializer = typeInfo.createSerializer(new SerializerConfigImpl())
    val outStream  = new ByteArrayOutputStream(1024)
    val outWrapper = new DataOutputViewStreamWrapper(outStream)
    serializer.serialize(value, outWrapper)
    serializer.deserialize(new DataInputViewStreamWrapper(new ByteArrayInputStream(outStream.toByteArray)))
  }

}
