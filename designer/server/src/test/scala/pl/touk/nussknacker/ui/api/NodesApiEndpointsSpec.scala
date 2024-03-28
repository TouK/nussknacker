package pl.touk.nussknacker.ui.api

import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.typed.{EnabledTypedFeatures, TypingResultGen}
import pl.touk.nussknacker.engine.graph.NodeDataGen
import pl.touk.nussknacker.test.{NuScalaTestAssertions, NuTapirSchemaTestHelpers}
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper
import pl.touk.nussknacker.ui.api.description.{NodesApiEndpoints, TypingDtoSchemas}

import java.time.Instant

class NodesApiEndpointsSpec
    extends AnyFreeSpecLike
    with ScalaCheckDrivenPropertyChecks
    with NuScalaTestAssertions
    with NuTapirSchemaTestHelpers {

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 5, minSize = 0)

  "TypingResult schemas shouldn't go out of sync with Codecs" in {
    val schema = prepareJsonSchemaFromTapirSchema(TypingDtoSchemas.typingResult)

    forAll(TypingResultGen.typingResultGen(EnabledTypedFeatures.All)) { typingResult =>
      val json = createJsonObjectFrom(typingResult)

      schema should validateJson(json)
      println("after verification " + Instant.now())
      println("----------------")
    }
  }

  "Node data check up" in {
    val schema = prepareJsonSchemaFromTapirSchema(NodesApiEndpoints.Dtos.NodeDataSchemas.nodeDataSchema)
    implicit val generatorDrivenConfig: PropertyCheckConfiguration =
      PropertyCheckConfiguration(minSuccessful = 1000, minSize = 0)
    forAll(NodeDataGen.nodeDataGen) { nodeData =>
      val json = createJsonObjectFrom(nodeData)
      schema should validateJson(json)
    }
  }

}
