package pl.touk.nussknacker.engine.lite.components

import org.apache.avro.Schema
import org.apache.avro.Schema.Type
import org.apache.avro.generic.GenericData.{EnumSymbol, Fixed}
import org.scalacheck.Gen
import pl.touk.nussknacker.engine.avro.{AvroSchemaCreator, AvroUtils}

import java.nio.ByteBuffer

object AvroGen {

  import scala.collection.JavaConverters._

  private val AllowedStringLetters = ('a' to 'z') ++ ('A' to 'Z')

  private val MaxFixedLength = 16
  private val MaxRecordFields = 4
  private val MaxEnumSize = 8
  private val MinStringLength = 4
  private val MaxStringLength = 16
  private val MapBaseKey = "key"

  private val random = new scala.util.Random

  def genSchema(config: ExcludedConfig): Gen[Schema] = genSchemaType(config).flatMap(genSchema(_, config.withoutRoot()))

  def genSchema(schemaType: Type, config: ExcludedConfig): Gen[Schema] = schemaType match {
    case Type.MAP => genSchema(config).flatMap(Schema.createMap)
    case Type.ARRAY => genSchema(config).flatMap(Schema.createArray)
    case Type.RECORD => listOfStringsGen(MaxRecordFields).flatMap(names =>
      Gen.sequence(names.map( name =>
        genSchema(config).flatMap(AvroSchemaCreator.createField(name, _))
      )).flatMap(fields =>
        AvroSchemaCreator.createRecord(fields.asScala.toList:_*)
      )
    )
    case Type.FIXED => intGen(MaxFixedLength).flatMap(size =>
      stringGen.flatMap(AvroSchemaCreator.createFixed(_, size))
    )
    case Type.ENUM => listOfStringsGen(MaxEnumSize).filter(_.nonEmpty).flatMap(values =>
      stringGen.flatMap(AvroSchemaCreator.createEnum(_, values))
    )
    case schemaType => genOne(Schema.create(schemaType))
  }

  def genSchemaType(config: ExcludedConfig): Gen[Type] = Gen.oneOf(Seq(
    Type.NULL, Type.BYTES, Type.INT, Type.LONG, Type.FLOAT, Type.DOUBLE, Type.STRING, Type.BOOLEAN,
    Type.FIXED, Type.ENUM, Type.ARRAY, Type.MAP, Type.RECORD
  )).filterNot(config.excluded.contains)

  def genValueForSchema(schema: Schema): Gen[Any] = schema.getType match {
    case Type.NULL => genOne(null)
    case Type.INT => Gen.choose(Integer.MIN_VALUE, Integer.MAX_VALUE)
    case Type.LONG => Gen.choose(java.lang.Long.MIN_VALUE, java.lang.Long.MAX_VALUE)
    case Type.STRING => stringGen
    case Type.BYTES => stringGen.map(_.getBytes("UTF-8")).map(ByteBuffer.wrap) //record bytes field can't be presented as array[bytes], value is casted to ByteBuffer, see: GenericDatumWriter.writeBytes
    case Type.BOOLEAN => Gen.oneOf(true, false)
    case Type.FLOAT => Gen.choose(java.lang.Float.MIN_VALUE, java.lang.Float.MAX_VALUE)
    case Type.DOUBLE => Gen.choose(java.lang.Double.MIN_VALUE, java.lang.Double.MAX_VALUE)
    case Type.ENUM => Gen.oneOf(schema.getEnumSymbols.asScala).map(symbol => new EnumSymbol(schema, symbol))
    case Type.FIXED => stringGen(schema.getFixedSize).map(str => {
      new Fixed(schema, str.getBytes("UTF-8"))
    })
    case Type.MAP => Gen.oneOf(genOne(Map.empty[String, Any].asJava), genValueForSchema(schema.getValueType).map(value => Map(MapBaseKey -> value).asJava))
    case Type.ARRAY => Gen.oneOf(genOne(List.empty.asJava), genValueForSchema(schema.getElementType).map(value => List(value).asJava))
    case Type.RECORD => Gen.sequence(schema.getFields.asScala.map(field =>
      genValueForSchema(field.schema()).flatMap(value => field.name() -> value)
    )).map(data => AvroUtils.createRecord(schema, data.asScala.toMap))
    case _ => throw new IllegalArgumentException(s"Unsupported schema: $schema")
  }

  def genValueWithSpELForSchema(schema: Schema): Gen[(Any, String)] =
    genValueForSchema(schema).map( value =>
      (value, AvroSinkOutputSpELConverter.convert(value))
    )

  def listOfStringsGen(maxSize: Int): Gen[List[String]] = Gen.containerOfN[List, String](randomInt(maxSize), stringGen).suchThat(_.nonEmpty)

  def stringGen: Gen[String] = Gen.chooseNum(MinStringLength, MaxStringLength).flatMap(stringGen)

  def stringGen(length: Int): Gen[String] = Gen.pick(length, AllowedStringLetters).map(_.mkString)

  def intGen(max: Int): Gen[Int] = Gen.choose(1, max)

  def genOne[T](one: T): Gen[T] = Gen.oneOf(Seq(one))

  private def randomInt(max: Int): Int = randomInt(1, max)

  private def randomInt(min: Int, max: Int): Int = random.nextInt(max - min + 1)

}

object ExcludedConfig {
  //Array can't be root schema, see: https://github.com/confluentinc/schema-registry/issues/1298
  private val DefaultRootExcluded: List[Type] = List(Type.ARRAY)

  val Base: ExcludedConfig = ExcludedConfig(DefaultRootExcluded, Nil)
}

case class ExcludedConfig(root: List[Type], global: List[Type]) {

  lazy val excluded: List[Type] = root ++ global

  def withoutRoot(): ExcludedConfig = copy(root = Nil)

  def withGlobal(excluded: Type*): ExcludedConfig = copy(global = this.global ++ excluded)

}
