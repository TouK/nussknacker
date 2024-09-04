package pl.touk.nussknacker.engine.flink.api.typeinfo.caseclass

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInfo
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.core.memory.{DataInputViewStreamWrapper, DataOutputViewStreamWrapper}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.reflect.{ClassTag, classTag}

class CaseClassSerializationTest extends AnyFunSuite with Matchers {

  private val executionConfig = new ExecutionConfig()

  private val bufferSize = 1024

  test("Simple case class should be the same after serialization and deserialization") {
    val input = SimpleCaseClass("value")

    val serializer   = getSerializer[SimpleCaseClass]
    val deserialized = serializeAndDeserialize(serializer, input)

    serializer shouldBe a[ScalaCaseClassSerializer[_]]
    deserialized shouldEqual input
  }

  test("Case class with field in body should be the same after serialization and deserialization") {
    val input = SimpleCaseClassWithAdditionalField("value")

    val serializer   = getSerializer[SimpleCaseClassWithAdditionalField]
    val deserialized = serializeAndDeserialize(serializer, input)

    serializer shouldBe a[ScalaCaseClassSerializer[_]]
    deserialized shouldEqual input
  }

  test("Case class with secondary constructor should be the same after serialization and deserialization") {
    val input = new SimpleCaseClassWithMultipleConstructors(2, "value")

    val serializer   = getSerializer[SimpleCaseClassWithMultipleConstructors]
    val deserialized = serializeAndDeserialize(serializer, input)

    serializer shouldBe a[ScalaCaseClassSerializer[_]]
    deserialized shouldEqual input
  }

  @silent("deprecated")
  private def getSerializer[T: ClassTag] =
    TypeExtractor
      .getForClass(classTag[T].runtimeClass.asInstanceOf[Class[T]])
      .createSerializer(executionConfig)

  private def serializeAndDeserialize[T](serializer: TypeSerializer[T], in: T): T = {
    val outStream  = new ByteArrayOutputStream(bufferSize)
    val outWrapper = new DataOutputViewStreamWrapper(outStream)

    serializer.serialize(in, outWrapper)
    serializer.deserialize(new DataInputViewStreamWrapper(new ByteArrayInputStream(outStream.toByteArray)))
  }

}

@TypeInfo(classOf[SimpleCaseClass.TypeInfoFactory])
final case class SimpleCaseClass(constructorField: String)

object SimpleCaseClass {
  class TypeInfoFactory extends CaseClassTypeInfoFactory[SimpleCaseClass]
}

@TypeInfo(classOf[SimpleCaseClassWithAdditionalField.TypeInfoFactory])
final case class SimpleCaseClassWithAdditionalField(constructorField: String) {
  val bodyField: String = "body " + constructorField
}

object SimpleCaseClassWithAdditionalField {
  class TypeInfoFactory extends CaseClassTypeInfoFactory[SimpleCaseClassWithAdditionalField]
}

@TypeInfo(classOf[SimpleCaseClassWithMultipleConstructors.TypeInfoFactory])
final case class SimpleCaseClassWithMultipleConstructors(firstField: String, secondField: Double) {
  val bodyField: String = "body " + firstField

  def this(someField: Int, someSecondField: String) = {
    this(someSecondField, someField)
  }

  def this(someField: Int, someSecondField: String, toIgnore: String) = {
    this(someSecondField, someField)
  }

}

object SimpleCaseClassWithMultipleConstructors {
  class TypeInfoFactory extends CaseClassTypeInfoFactory[SimpleCaseClassWithMultipleConstructors]
}
