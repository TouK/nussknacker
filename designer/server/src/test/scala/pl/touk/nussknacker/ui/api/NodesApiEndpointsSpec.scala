package pl.touk.nussknacker.ui.api

import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.typed.{EnabledTypedFeatures, TypingResultGen}
import pl.touk.nussknacker.test.{NuScalaTestAssertions, NuTapirExtensions}
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper

class NodesApiEndpointsSpec
    extends AnyFreeSpecLike
    with ScalaCheckDrivenPropertyChecks
    with NuScalaTestAssertions
    with NuTapirExtensions {

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 5, minSize = 0)

  "TypingResult schemas shouldn't go out of sync with Codecs" in {
    val schema = prepareJsonSchemaFromTapirSchema(TypingDtoSchemas.typingResult)

    forAll(TypingResultGen.typingResultGen(EnabledTypedFeatures.All)) { typingResult =>
      val json = createJsonObjectFrom(typingResult)

      schema should validateJson(json)
    }
  }

}
