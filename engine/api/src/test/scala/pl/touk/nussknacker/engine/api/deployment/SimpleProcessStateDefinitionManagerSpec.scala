package pl.touk.nussknacker.engine.api.deployment

import org.scalatest.{FunSpec, Inside, Matchers}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager

class SimpleProcessStateDefinitionManagerSpec  extends FunSpec with Matchers with Inside {
  it ("should create name for status.name with small letter") {
    val status = SimpleProcessStateDefinitionManager.statusName(new NotEstablishedStateStatus("not_deployed_yet"))
    status shouldBe "NotDeployedYet"
  }

  it ("should create name for status.name with big letter") {
    val status = SimpleProcessStateDefinitionManager.statusName(new NotEstablishedStateStatus("NOT_DEPLOYED_YET"))
    status shouldBe "NotDeployedYet"
  }

  it ("should create name for status.name with mix letter") {
    val status = SimpleProcessStateDefinitionManager.statusName(new NotEstablishedStateStatus("NoT_DEPloyED_yET"))
    status shouldBe "NotDeployedYet"
  }
}
