package pl.touk.nussknacker.engine.flink.test

import cats.data.NonEmptyList
import cats.implicits.toFunctorOps
import io.circe.Decoder.Result
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.parser.parse
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, HCursor, Json, Printer}
import io.circe.syntax._
import org.apache.commons.io.{FileUtils, IOUtils}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Inside}
import org.springframework.util.ClassUtils
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.typed.typing.{TypedClass, TypingResult}
import pl.touk.nussknacker.engine.api.typed.{TypeEncoders, TypingResultDecoder}
import pl.touk.nussknacker.engine.api.generics.{MethodTypeInfo, Parameter}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.definition.TypeInfos.{ClazzDefinition, FunctionalMethodInfo, MethodInfo, StaticMethodInfo}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import java.io.File
import java.nio.charset.StandardCharsets
import pl.touk.nussknacker.engine.api.CirceUtil._

trait ClassExtractionBaseTest extends AnyFunSuite with Matchers with Inside {

  protected def model: ModelData

  protected def outputResource: String

  // We replace typeFunction with null because there is no way to serialize
  // it with typeFunction.
  private def simplifyMethodInfo(info: MethodInfo): MethodInfo = info match {
    case x: StaticMethodInfo => x
    case x: FunctionalMethodInfo => x.copy(typeFunction = null)
  }

  // We need to sort methods with identical names to make checks ignore order.
  private def assertClassEquality(left: ClazzDefinition, right: ClazzDefinition): Unit = {
    def simplifyMap(m: Map[String, List[MethodInfo]]): Map[String, List[MethodInfo]] =
      m.mapValuesNow(_.map(simplifyMethodInfo))

    def assertMethodMapEquality(left: Map[String, List[MethodInfo]], right: Map[String, List[MethodInfo]]): Unit = {
      val processedLeft = simplifyMap(left)
      val processedRight = simplifyMap(right)
      processedLeft.keySet shouldBe processedRight.keySet
      processedLeft.keySet.foreach(k => processedLeft(k) should contain theSameElementsAs processedRight(k))
    }

    val ClazzDefinition(_, leftMethods, staticLeftMethods) = left
    val ClazzDefinition(_, rightMethods, staticRightMethods) = right
    assertMethodMapEquality(leftMethods, rightMethods)
    assertMethodMapEquality(staticLeftMethods, staticRightMethods)
  }

  test("check extracted class for model") {
    val types = ProcessDefinitionExtractor.extractTypes(model.processWithObjectsDefinition)
//    printFoundClasses(types)
    if (Option(System.getProperty("CLASS_EXTRACTION_PRINT")).exists(_.toBoolean)) {
      FileUtils.write(new File(s"/tmp/${getClass.getSimpleName}-result.json"), encode(types), StandardCharsets.UTF_8)
    }

    val parsed =  parse(IOUtils.toString(getClass.getResourceAsStream(outputResource))).toOption.get
    val decoded = decode(parsed)
    checkGeneratedClasses(types, decoded)
  }

  //use for debugging...
  private def printFoundClasses(types: Set[ClazzDefinition]): String = {
    types.flatMap { cd =>
      cd.clazzName ::
        (cd.methods ++ cd.staticMethods)
          .flatMap(_._2)
          .flatMap(mi => mi.signatures.head.result :: mi.signatures.head.parametersToList.map(_.refClazz))
          .toList
    }.collect {
      case e: TypedClass => e.klass.getName
    }.toList.sorted.mkString("\n")
  }

  //We don't do 'types shouldBe decoded' to avoid unreadable messages
  private def checkGeneratedClasses(types: Set[ClazzDefinition], decoded: Set[ClazzDefinition]): Unit = {
    val names = types.map(_.clazzName.klass.getName)
    val decodedNames = types.map(_.clazzName.klass.getName)

    withClue(s"New classes: ${names -- decodedNames} ") {
      (names -- decodedNames) shouldBe Set.empty
    }
    withClue(s"Removed classes: ${decodedNames -- names} ") {
      (decodedNames -- names) shouldBe Set.empty
    }

    val decodedMap = decoded.map(k => k.clazzName.klass -> k).toMap[Class[_], ClazzDefinition]
    types.foreach { clazzDefinition =>
      val clazz = clazzDefinition.clazzName.klass
      withClue(s"$clazz does not match: ") {
        val decoded = decodedMap.getOrElse(clazz, throw new AssertionError(s"No class $clazz"))

        def checkMethods(getMethods: ClazzDefinition => Map[String, List[MethodInfo]]): Unit = {
          val methods = getMethods(clazzDefinition)
          val decodedMethods = getMethods(decoded)
          methods.keys shouldBe decodedMethods.keys
          methods.foreach { case (k, v) =>
            withClue(s"$clazz with method: $k does not match, ${v.asJson}, ${decodedMethods(k).asJson}: ") {
              v.map(simplifyMethodInfo) should contain theSameElementsAs decodedMethods(k)
            }
          }
        }
        checkMethods(_.methods)
        checkMethods(_.staticMethods)

        assertClassEquality(clazzDefinition, decoded)
      }
    }
  }

  private implicit val typingResultEncoder: Encoder[TypingResult] = TypeEncoders.typingResultEncoder.mapJsonObject { obj =>
    // it will work only on first level unfortunately
    obj.filter {
      case ("display", _) => false
      case ("params", params) => params.asArray.get.nonEmpty
      case ("type", typ) => typ != Json.fromString("TypedClass")
      case ("refClazzName", _) => !obj("type").contains(Json.fromString("Unknown"))
      case _ => true
    }
  }
  private implicit val parameterE: Encoder[Parameter] = deriveConfiguredEncoder
  private implicit val methodTypeInfoE: Encoder[MethodTypeInfo] = deriveConfiguredEncoder
  private implicit val staticMethodInfoE: Encoder[StaticMethodInfo] = deriveConfiguredEncoder
  private implicit val functionalMethodInfoE: Encoder[FunctionalMethodInfo] = (a: FunctionalMethodInfo) => Json.obj(
    ("signatures", a.signatures.asJson),
    ("name", a.name.asJson),
    ("description", a.description.asJson)
  )
  private implicit val methodInfoE: Encoder[MethodInfo] = {
    case x: StaticMethodInfo => x.asJson
    case x: FunctionalMethodInfo => x.asJson
  }
  private implicit val typedClassE: Encoder[TypedClass] = typingResultEncoder.contramap[TypedClass](identity)
  private implicit val clazzDefinitionE: Encoder[ClazzDefinition] = deriveConfiguredEncoder


  private def encode(types: Set[ClazzDefinition]) = {
    val encoded = types.toList.sortBy(_.clazzName.klass.getName).asJson
    val printed = Printer.spaces2SortKeys.copy(colonLeft = "", dropNullValues = true).print(encoded)
    printed
      .replaceAll(raw"""\{\s*("refClazzName": ".*?")\s*}""", "{$1}")
      .replaceAll(raw"""\{\s*("type": ".*?")\s*}""", "{$1}")
      .replaceAll(raw"""\{\s*("name": ".*?",)\s*("refClazz": \{[^{]*?})\s*}""", "{$1 $2}")
      .replaceAll(raw"""\{\s*}""", "{}")
      .replaceAll(raw"""\[\s*]""", "[]")
  }

  private def decode(json: Json): Set[ClazzDefinition] = {
    val typeInformation = new TypingResultDecoder(ClassUtils.forName(_, getClass.getClassLoader))
    implicit val typingResultDecoder: Decoder[TypingResult] = typeInformation.decodeTypingResults.prepare(cursor => cursor.withFocus { json =>
      json.asObject.map { obj =>
        val withRecoveredEmptyParams = if (!obj.contains("params")) obj.add("params", Json.arr()) else obj
        val withRecoveredTypeClassType = if (!withRecoveredEmptyParams.contains("type")) withRecoveredEmptyParams.add("type", Json.fromString("TypedClass")) else withRecoveredEmptyParams
        val withRecoveredTypeRefClazzName = if (!withRecoveredTypeClassType.contains("refClazzName") && withRecoveredTypeClassType("type").contains(Json.fromString("Unknown")))
          withRecoveredTypeClassType.add("refClazzName", Json.fromString("java.lang.Object"))
        else
          withRecoveredTypeClassType
        Json.fromJsonObject(withRecoveredTypeRefClazzName)
      }.getOrElse(json)
    })
    implicit val parameterD: Decoder[Parameter] = deriveConfiguredDecoder
    implicit val methodTypeInfoD: Decoder[MethodTypeInfo] = deriveConfiguredDecoder
    implicit val staticMethodInfoD: Decoder[StaticMethodInfo] = deriveConfiguredDecoder
    implicit val functionalMethodInfoD: Decoder[FunctionalMethodInfo] = (c: HCursor) => for {
      signatures <- c.downField("signatures").as[NonEmptyList[MethodTypeInfo]]
      name <- c.downField("name").as[String]
      description <- c.downField("description").as[Option[String]]
    } yield FunctionalMethodInfo(null, signatures, name, description)
    implicit val methodInfoD: Decoder[MethodInfo] = staticMethodInfoD.widen or functionalMethodInfoD.widen
    implicit val typedClassD: Decoder[TypedClass] = typingResultDecoder.map(_.asInstanceOf[TypedClass])
    implicit val clazzDefinitionD: Decoder[ClazzDefinition] = deriveConfiguredDecoder

    val decoded = json.as[Set[ClazzDefinition]]
    inside(decoded) {
      case Right(value) => value
    }
  }
}
