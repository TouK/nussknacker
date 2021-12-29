package pl.touk.nussknacker.engine.compile

import org.scalatest.{FunSuite, Matchers, OptionValues}
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

class SpelConversionServiceOverrideSpec extends FunSuite with Matchers with OptionValues {

  import spel.Implicits._

  class MyProcessConfigCreator extends EmptyProcessConfigCreator

  test("") {

  }

}
