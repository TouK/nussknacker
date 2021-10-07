package pl.touk.nussknacker.engine.util

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Multiplicity, Many, One}

class MultiplicitySpec extends FlatSpec with Matchers {
  it should "return One element for single element sequence" in {
    Multiplicity(1 :: Nil) shouldBe One(1)
  }
  it should "return Empty element for empty sequence" in {
    Multiplicity(Nil) shouldBe Empty()
  }
  it should "return Many for more than one element" in {
    Multiplicity(1 :: 2 :: Nil) shouldBe Many(1 :: 2 :: Nil)
  }

}
