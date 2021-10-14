package pl.touk.nussknacker.engine.flink.test

import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.parser.parse
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, Json, Printer}
import org.apache.commons.io.IOUtils
import org.scalatest.{FunSuite, Matchers}
import org.springframework.util.ClassUtils
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.typed.typing.{TypedClass, TypingResult}
import pl.touk.nussknacker.engine.api.typed.{TypeEncoders, TypingResultDecoder}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.definition.TypeInfos.{ClazzDefinition, MethodInfo, Parameter}
import pl.touk.nussknacker.engine.api.CirceUtil._

trait ClassExtractionBaseTest extends FunSuite with Matchers {

  protected def model: ModelData

  protected def outputResource: String

  test("check extracted class for model") {
    val types = ProcessDefinitionExtractor.extractTypes(model.processWithObjectsDefinition)
    val parsed =  parse(IOUtils.toString(getClass.getResourceAsStream(outputResource))).right.get
    val decoded = decode(parsed)

//    printFoundClasses(types)
//    println(encode(types))
    checkGeneratedClasses(types, decoded)
  }

  //use for debugging...
  private def printFoundClasses(types: Set[ClazzDefinition]): String = {
    types.flatMap { cd =>
      cd.clazzName :: (cd.methods ++ cd.staticMethods).flatMap(_._2).flatMap(mi => mi.refClazz :: mi.parameters.map(_.refClazz)).toList
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
              v shouldBe decodedMethods(k)
            }
          }
        }
        checkMethods(_.methods)
        checkMethods(_.staticMethods)
        clazzDefinition shouldBe decoded
      }
    }
  }

  private def encode(types: Set[ClazzDefinition]) = {
    implicit val typingResultEncoder: Encoder[TypingResult] = TypeEncoders.typingResultEncoder.mapJsonObject { obj =>
      // it will work only on first level unfortunately
      obj.filter {
        case ("display", _) => false
        case ("params", params) => params.asArray.get.nonEmpty
        case ("type", typ) => typ != Json.fromString("TypedClass")
        case ("refClazzName", _) => !obj("type").contains(Json.fromString("Unknown"))
        case _ => true
      }
    }

    implicit val parameterD: Encoder[Parameter] = deriveConfiguredEncoder[Parameter]
    implicit val methodInfoD: Encoder[MethodInfo] = deriveConfiguredEncoder[MethodInfo]
    implicit val typedClassD: Encoder[TypedClass] = typingResultEncoder.contramap[TypedClass](identity)

    implicit val clazzDefinitionD: Encoder[ClazzDefinition] = deriveConfiguredEncoder[ClazzDefinition]
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
    implicit val typingResultEncoder: Decoder[TypingResult] = typeInformation.decodeTypingResults.prepare(cursor => cursor.withFocus { json =>
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
    implicit val methodInfoD: Decoder[MethodInfo] = deriveConfiguredDecoder
    implicit val typedClassD: Decoder[TypedClass] = typingResultEncoder.map(k => k.asInstanceOf[TypedClass])

    implicit val clazzDefinitionD: Decoder[ClazzDefinition] = deriveConfiguredDecoder

    val decoded = json.as[Set[ClazzDefinition]]
    decoded.right.get
  }


}
