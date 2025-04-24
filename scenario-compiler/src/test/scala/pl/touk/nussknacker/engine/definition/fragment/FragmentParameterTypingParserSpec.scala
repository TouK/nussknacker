package pl.touk.nussknacker.engine.definition.fragment

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing.TypedClass
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinitionSet, ClassDefinitionTestUtils}

import scala.util.{Failure, Success}

class FragmentParameterTypingParserSpec extends AnyFunSuite with Matchers {
  private val classLoader = getClass.getClassLoader

  private val classDefinitionExtractor = ClassDefinitionTestUtils.DefaultExtractor

  private val clazzDefinitions: ClassDefinitionSet = ClassDefinitionSet(
    Set(
      classDefinitionExtractor.extract(classOf[String]),
      classDefinitionExtractor.extract(classOf[Integer]),
    )
  )

  private val fragmentParameterTypingParser = new FragmentParameterTypingParser(classLoader, clazzDefinitions)

  test("should parse nested generic Map type into TypedClass") {
    val triedTypingResult =
      fragmentParameterTypingParser.parseClassNameToTypingResult("Map[List[Integer], Map[String, List[Integer]]]")

    triedTypingResult match {
      case Failure(ex) => fail(s"Unexpected failure: $ex")
      case Success(tc: TypedClass) =>
        tc.display shouldBe "Map[List[Integer],Map[String,List[Integer]]]"
      case Success(tr) =>
        fail(s"Expected a result type which is TypedClass but got instead: ${tr.getClass.getSimpleName}")
    }
  }

}
