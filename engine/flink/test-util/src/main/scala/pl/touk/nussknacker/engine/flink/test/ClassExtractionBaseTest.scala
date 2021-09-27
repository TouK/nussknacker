package pl.touk.nussknacker.engine.flink.test

import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import io.circe.parser.parse
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Json}
import org.apache.commons.io.IOUtils
import org.scalatest.{FunSuite, Matchers}
import org.springframework.util.ClassUtils
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.typed.TypingResultDecoder
import pl.touk.nussknacker.engine.api.typed.typing.TypedClass
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

    //printFoundClasses(types)
    //println(types.toList.sortBy(_.clazzName.klass.getName).asJson.spaces2SortKeys)
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
            withClue(s"$clazz with method: $k does not match, ${v.asJson}, ${methods(k).asJson}: ") {
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

  private def decode(json: Json): Set[ClazzDefinition] = {
    val typeInformation = new TypingResultDecoder(ClassUtils.forName(_, getClass.getClassLoader))
    import typeInformation.decodeTypingResults

    implicit val parameterD: Decoder[Parameter] = deriveConfiguredDecoder
    implicit val methodInfoD: Decoder[MethodInfo] = deriveConfiguredDecoder
    implicit val typedClassD: Decoder[TypedClass] = typeInformation.decodeTypingResults.map(k => k.asInstanceOf[TypedClass])

    implicit val clazzDefinitionD: Decoder[ClazzDefinition] = deriveConfiguredDecoder

    json.as[Set[ClazzDefinition]].right.get
  }


}
