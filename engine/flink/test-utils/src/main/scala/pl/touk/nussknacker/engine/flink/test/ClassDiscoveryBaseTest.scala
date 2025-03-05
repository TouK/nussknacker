package pl.touk.nussknacker.engine.flink.test

import cats.data.NonEmptyList
import cats.implicits.toFunctorOps
import com.typesafe.scalalogging.LazyLogging
import io.circe._
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.parser.parse
import io.circe.syntax.EncoderOps
import org.apache.commons.io.FileUtils
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.springframework.util.ClassUtils
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.generics.{MethodTypeInfo, Parameter}
import pl.touk.nussknacker.engine.api.typed.{TypeEncoders, TypingResultDecoder}
import pl.touk.nussknacker.engine.api.typed.typing.{TypedClass, TypingResult}
import pl.touk.nussknacker.engine.definition.clazz.{
  ClassDefinition,
  FunctionalMethodDefinition,
  MethodDefinition,
  StaticMethodDefinition
}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.ResourceLoader

import java.io.File
import java.nio.charset.StandardCharsets
import scala.util.Properties

trait ClassDiscoveryBaseTest extends AnyFunSuite with Matchers with Inside with LazyLogging {

  protected def model: ModelData

  protected def outputResource: String

  // We replace typeFunction with null because there is no way to serialize
  // it with typeFunction.
  private def simplifyMethodDefinition(definition: MethodDefinition): MethodDefinition = definition match {
    case x: StaticMethodDefinition     => x
    case x: FunctionalMethodDefinition => x.copy(typeFunction = null)
  }

  // We need to sort methods with identical names to make checks ignore order.
  private def assertClassEquality(left: ClassDefinition, right: ClassDefinition): Unit = {
    def simplifyMap(m: Map[String, List[MethodDefinition]]): Map[String, List[MethodDefinition]] =
      m.mapValuesNow(_.map(simplifyMethodDefinition))

    def assertMethodMapEquality(
        left: Map[String, List[MethodDefinition]],
        right: Map[String, List[MethodDefinition]]
    ): Unit = {
      val processedLeft  = simplifyMap(left)
      val processedRight = simplifyMap(right)
      processedLeft.keySet shouldBe processedRight.keySet
      processedLeft.keySet.foreach(k => processedLeft(k) should contain theSameElementsAs processedRight(k))
    }

    val ClassDefinition(_, leftMethods, staticLeftMethods)   = left
    val ClassDefinition(_, rightMethods, staticRightMethods) = right
    assertMethodMapEquality(leftMethods, rightMethods)
    assertMethodMapEquality(staticLeftMethods, staticRightMethods)
  }

  test("check extracted class for model") {
    val types = model.modelDefinitionWithClasses.classDefinitions.all
    if (Option(System.getenv("CLASS_EXTRACTION_PRINT")).exists(_.toBoolean)) {
      val fileName = s"${Properties.tmpDir}/${getClass.getSimpleName}-result.json"
      logger.info(s"CLASS_EXTRACTION_PRINT is set. The file JSON file will be stored in '$fileName'")
      FileUtils.write(new File(fileName), encode(types), StandardCharsets.UTF_8)
    }
    val parsed  = parse(ResourceLoader.load(outputResource)).toOption.get
    val decoded = decode(parsed)

    checkGeneratedClasses(types, decoded)
  }

  // We don't do 'types shouldBe decoded' to avoid unreadable messages
  private def checkGeneratedClasses(types: Set[ClassDefinition], decoded: Set[ClassDefinition]): Unit = {
    val names        = types.map(_.getClazz.getName)
    val decodedNames = decoded.map(_.getClazz.getName)

    withClue(s"New classes") {
      (names -- decodedNames) shouldBe empty
    }
    withClue(s"Removed classes") {
      (decodedNames -- names) shouldBe empty
    }

    val decodedMap = decoded.map(k => k.getClazz -> k).toMap[Class[_], ClassDefinition]
    types.foreach { clazzDefinition =>
      val clazz = clazzDefinition.getClazz
      withClue(s"$clazz does not match: ") {
        val decoded = decodedMap.getOrElse(clazz, throw new AssertionError(s"No class $clazz"))

        def checkMethods(getMethods: ClassDefinition => Map[String, List[MethodDefinition]]): Unit = {
          val methods        = getMethods(clazzDefinition)
          val decodedMethods = getMethods(decoded)
          methods.keySet shouldBe decodedMethods.keySet
          methods.foreach { case (k, v) =>
            withClue(s"$clazz with method: $k does not match, ${v.asJson}, ${decodedMethods(k).asJson}: ") {
              v.map(simplifyMethodDefinition) should contain theSameElementsAs decodedMethods(k)
            }
          }
        }
        checkMethods(_.methods)
        checkMethods(_.staticMethods)

        assertClassEquality(clazzDefinition, decoded)
      }
    }
  }

  private implicit val typingResultEncoder: Encoder[TypingResult] = TypeEncoders.typingResultEncoder.mapJsonObject {
    obj =>
      // it will work only on first level unfortunately
      obj.filter {
        case ("display", _)      => false
        case ("params", params)  => params.asArray.get.nonEmpty
        case ("type", typ)       => typ != Json.fromString("TypedClass")
        case ("refClazzName", _) => !obj("type").contains(Json.fromString("Unknown"))
        case _                   => true
      }
  }

  private implicit val parameterE: Encoder[Parameter]                           = deriveConfiguredEncoder
  private implicit val methodTypeInfoE: Encoder[MethodTypeInfo]                 = deriveConfiguredEncoder
  private implicit val staticMethodDefinitionE: Encoder[StaticMethodDefinition] = deriveConfiguredEncoder

  private implicit val functionalMethodDefinitionE: Encoder[FunctionalMethodDefinition] =
    (a: FunctionalMethodDefinition) =>
      Json.obj(
        ("signatures", a.signatures.asJson),
        ("name", a.name.asJson),
        ("description", a.description.asJson)
      )

  private implicit val methodDefinitionE: Encoder[MethodDefinition] = {
    case x: StaticMethodDefinition     => x.asJson
    case x: FunctionalMethodDefinition => x.asJson
  }

  private implicit val typedClassE: Encoder[TypedClass]           = typingResultEncoder.contramap[TypedClass](identity)
  private implicit val classDefinitionE: Encoder[ClassDefinition] = deriveConfiguredEncoder

  private def encode(types: Set[ClassDefinition]) = {
    val encoded = types.toList.sortBy(_.getClazz.getName).asJson
    val printed = Printer.spaces2SortKeys.copy(colonLeft = "", dropNullValues = true).print(encoded)
    printed
      .replaceAll(raw"""\{\s*("refClazzName": ".*?")\s*}""", "{$1}")
      .replaceAll(raw"""\{\s*("type": ".*?")\s*}""", "{$1}")
      .replaceAll(raw"""\{\s*("name": ".*?",)\s*("refClazz": \{[^{]*?})\s*}""", "{$1 $2}")
      .replaceAll(raw"""\{\s*}""", "{}")
      .replaceAll(raw"""\[\s*]""", "[]")
  }

  private def decode(json: Json): Set[ClassDefinition] = {
    val typeInformation = new TypingResultDecoder(ClassUtils.forName(_, getClass.getClassLoader))
    implicit val typingResultDecoder: Decoder[TypingResult] = typeInformation.decodeTypingResults.prepare(cursor =>
      cursor.withFocus { json =>
        json.asObject
          .map { obj =>
            val withRecoveredEmptyParams = if (!obj.contains("params")) obj.add("params", Json.arr()) else obj
            val withRecoveredTypeClassType =
              if (!withRecoveredEmptyParams.contains("type"))
                withRecoveredEmptyParams.add("type", Json.fromString("TypedClass"))
              else withRecoveredEmptyParams
            val withRecoveredTypeRefClazzName =
              if (!withRecoveredTypeClassType.contains("refClazzName") && withRecoveredTypeClassType("type")
                  .contains(Json.fromString("Unknown")))
                withRecoveredTypeClassType.add("refClazzName", Json.fromString("java.lang.Object"))
              else
                withRecoveredTypeClassType
            Json.fromJsonObject(withRecoveredTypeRefClazzName)
          }
          .getOrElse(json)
      }
    )
    implicit val parameterD: Decoder[Parameter]                           = deriveConfiguredDecoder
    implicit val methodTypeInfoD: Decoder[MethodTypeInfo]                 = deriveConfiguredDecoder
    implicit val staticMethodDefinitionD: Decoder[StaticMethodDefinition] = deriveConfiguredDecoder
    implicit val functionalMethodDefinitionD: Decoder[FunctionalMethodDefinition] = (c: HCursor) =>
      for {
        signatures  <- c.downField("signatures").as[NonEmptyList[MethodTypeInfo]]
        name        <- c.downField("name").as[String]
        description <- c.downField("description").as[Option[String]]
      } yield FunctionalMethodDefinition(null, signatures, name, description)
    implicit val methodDefinitionD: Decoder[MethodDefinition] =
      staticMethodDefinitionD.widen or functionalMethodDefinitionD.widen
    implicit val typedClassD: Decoder[TypedClass]           = typingResultDecoder.map(_.asInstanceOf[TypedClass])
    implicit val classDefinitionD: Decoder[ClassDefinition] = deriveConfiguredDecoder

    val decoded = json.as[Set[ClassDefinition]]
    inside(decoded) { case Right(value) =>
      value
    }
  }

}
