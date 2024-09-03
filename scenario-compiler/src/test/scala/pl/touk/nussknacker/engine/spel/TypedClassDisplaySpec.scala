package pl.touk.nussknacker.engine.spel

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing.Typed

import java.util
import scala.language.implicitConversions

class TypedClassDisplaySpec extends AnyFunSuite with Matchers {

  test("parsing array display") {
    Typed.typedClass(classOf[Array[String]]).display should equal("List[String]")
  }

  test("parsing nested arrays display") {
    Typed.typedClass(classOf[Array[Array[String]]]).display should equal("List[List[String]]")
  }

  test("parsing nested class display") {
    Typed
      .genericTypeClass(
        classOf[util.AbstractMap.SimpleEntry[String, String]],
        List(Typed(classOf[String]), Typed(classOf[String]))
      )
      .display should equal("SimpleEntry[String,String]")
  }

  test("parsing anonymous class display") {
    Typed.typedClass(new java.io.Serializable {}.getClass).display should equal("")
  }

}
