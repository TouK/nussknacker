package pl.touk.nussknacker.engine.lite.components.utils

import org.apache.avro.Schema
import org.apache.avro.Schema.Type
import org.apache.avro.generic.GenericData.{EnumSymbol, Fixed}
import org.scalacheck.Gen
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object AvroGen {

  import scala.jdk.CollectionConverters._

  private val AllowedStringLetters = ('a' to 'z') ++ ('A' to 'Z')

  private val MaxFixedLength  = 16
  private val MaxRecordFields = 4
  private val MaxEnumSize     = 8
  private val MinStringLength = 4
  private val MaxStringLength = 16
  private val MapBaseKey      = "key"

  def genSchema(config: ExcludedConfig): Gen[Schema] = genSchemaType(config).flatMap(genSchema(_, config.withoutRoot()))

  def genSchema(schemaType: Type, config: ExcludedConfig): Gen[Schema] = schemaType match {
    case Type.MAP   => genSchema(config).flatMap(Schema.createMap)
    case Type.ARRAY => genSchema(config).flatMap(Schema.createArray)
    case Type.RECORD =>
      nonEmptyDistinctListOfStringsGen(MaxRecordFields).flatMap(names =>
        Gen
          .sequence(names.map(name => genSchema(config).flatMap(AvroSchemaCreator.createField(name, _))))
          .flatMap(fields => AvroSchemaCreator.createRecord(fields.asScala.toList: _*))
      )
    case Type.FIXED => intGen(MaxFixedLength).flatMap(size => stringGen.flatMap(AvroSchemaCreator.createFixed(_, size)))
    case Type.ENUM =>
      nonEmptyDistinctListOfStringsGen(MaxEnumSize).flatMap(values =>
        stringGen.flatMap(AvroSchemaCreator.createEnum(_, values))
      )
    case schemaType => Gen.const(Schema.create(schemaType))
  }

  def genSchemaType(config: ExcludedConfig): Gen[Type] = Gen
    .oneOf(
      Seq(
        Type.NULL,
        Type.BYTES,
        Type.INT,
        Type.LONG,
        Type.FLOAT,
        Type.DOUBLE,
        Type.STRING,
        Type.BOOLEAN,
        Type.FIXED,
        Type.ENUM,
        Type.ARRAY,
        Type.MAP,
        Type.RECORD
      )
    )
    .filterNot(config.excluded.contains)

  def genValueForSchema(schema: Schema): Gen[Any] = schema.getType match {
    case Type.NULL   => Gen.const(null)
    case Type.INT    => Gen.choose(Integer.MIN_VALUE, Integer.MAX_VALUE)
    case Type.LONG   => Gen.choose(java.lang.Long.MIN_VALUE, java.lang.Long.MAX_VALUE)
    case Type.STRING => stringGen
    case Type.BYTES =>
      stringGen
        .map(_.getBytes(StandardCharsets.UTF_8))
        .map(
          ByteBuffer.wrap
        ) // record bytes field can't be presented as array[bytes], value is casted to ByteBuffer, see: GenericDatumWriter.writeBytes
    case Type.BOOLEAN => Gen.oneOf(true, false)
    case Type.FLOAT   => Gen.choose(java.lang.Float.MIN_VALUE, java.lang.Float.MAX_VALUE)
    case Type.DOUBLE  => Gen.choose(java.lang.Double.MIN_VALUE, java.lang.Double.MAX_VALUE)
    case Type.ENUM    => Gen.oneOf(schema.getEnumSymbols.asScala).map(symbol => new EnumSymbol(schema, symbol))
    case Type.FIXED =>
      stringGen(schema.getFixedSize).map(str => {
        new Fixed(schema, str.getBytes(StandardCharsets.UTF_8))
      })
    case Type.MAP   => genValueForSchema(schema.getValueType).map(value => Map(MapBaseKey -> value).asJava)
    case Type.ARRAY => genValueForSchema(schema.getElementType).map(value => List(value).asJava)
    case Type.RECORD =>
      Gen
        .sequence(
          schema.getFields.asScala.map(field =>
            genValueForSchema(field.schema()).flatMap(value => field.name() -> value)
          )
        )
        .map(data => AvroUtils.createRecord(schema, data.asScala.toMap))
    case _ => throw new IllegalArgumentException(s"Unsupported schema: $schema")
  }

  def nonEmptyDistinctListOfStringsGen(maxSize: Int): Gen[List[String]] =
    Gen.containerOfN[Set, String](maxSize, stringGen).suchThat(_.nonEmpty).map(_.toList)

  def stringGen: Gen[String] = Gen.chooseNum(MinStringLength, MaxStringLength).flatMap(stringGen)

  def stringGen(length: Int): Gen[String] = Gen.pick(length, AllowedStringLetters).map(_.mkString)

  def intGen(max: Int): Gen[Int] = Gen.choose(1, max)

}

object ExcludedConfig {
  // Array can't be root schema, see: https://github.com/confluentinc/schema-registry/issues/1298
  private val DefaultRootExcluded: List[Type] = List(Type.ARRAY)

  val Base: ExcludedConfig = ExcludedConfig(DefaultRootExcluded, Nil)
}

case class ExcludedConfig(root: List[Type], global: List[Type]) {

  lazy val excluded: List[Type] = root ++ global

  def withoutRoot(): ExcludedConfig = copy(root = Nil)

  def withGlobal(excluded: Type*): ExcludedConfig = copy(global = this.global ++ excluded)

}
