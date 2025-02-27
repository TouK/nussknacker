package pl.touk.nussknacker.ui.api

import io.circe.syntax.EncoderOps
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.typed.{EnabledTypedFeatures, TypingResultGen}
import pl.touk.nussknacker.test.{NuScalaTestAssertions, NuTapirSchemaTestHelpers}
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper
import pl.touk.nussknacker.test.utils.generators.NodeDataGen
import pl.touk.nussknacker.ui.api.description.{NodesApiEndpoints, TypingDtoSchemas}

import scala.util.control.Breaks.{break, breakable}

class NodesApiEndpointsSpec
    extends AnyFreeSpecLike
    with ScalaCheckDrivenPropertyChecks
    with NuScalaTestAssertions
    with NuTapirSchemaTestHelpers {

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 1000, minSize = 0)

  "TypingResult schemas shouldn't go out of sync with Codecs" in {
    val schema = prepareJsonSchemaFromTapirSchema(TypingDtoSchemas.typingResult)

    forAll(TypingResultGen.typingResultGen(EnabledTypedFeatures.All)) { typingResult =>
      val json = typingResult.asJson
      breakable {
//        This test gets stuck when validating schema of a too big size
//        There is no problem with creating json schema, just with validating it, so introduced a size limit
//        This also allows us to test 1000 examples instead of 5 and don't worry for the test to take too long
        if (json.spaces2.length() > 750) {
          break()
        } else {
          schema should validateJson(json)
        }
      }

    }
  }

  "Node data check up" in {
    val schema = prepareJsonSchemaFromTapirSchema(NodesApiEndpoints.Dtos.NodeDataSchemas.nodeDataSchema)

    forAll(NodeDataGen.nodeDataGen) { nodeData =>
      val json = nodeData.asJson

      schema should validateJson(json)
    }
  }

}
