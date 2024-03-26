package pl.touk.nussknacker.ui.api

import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.typed.{EnabledTypedFeatures, TypingResultGen}
import pl.touk.nussknacker.test.{NuScalaTestAssertions, NuTapirExtensions}
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper
import pl.touk.nussknacker.ui.api.description.{NodesApiEndpoints, TypingDtoSchemas}

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

  "Node data check up" in {
    val schema = prepareJsonSchemaFromTapirSchema(NodesApiEndpoints.Dtos.NodeDataSchemas.nodeDataSchema)

    val enricher =
      s"""{\"additionalFields\":{\"layoutData\":{\"x\":-91,\"y\":81}},\"service\":{\"id\":\"enricher\",\"parameters\":[{\"name\":\"param\",\"expression\":{\"language\":\"spel\",\"expression\":\"'default value'\"}},{\"name\":\"tariffType\",\"expression\":{\"language\":\"spel\",\"expression\":\"T(pl.touk.nussknacker.engine.management.sample.TariffType).NORMAL\"}}]},\"output\":\"output\",\"type\":\"Enricher\",\"branchParametersTemplate\":[],\"id\":\"abc\"}"""
    val filter =
      s"""{\"additionalFields\":{\"layoutData":{\"x\":-179,\"y\":-170}},\"expression\":{\"language\":\"spel\",\"expression\":\"true\"},\"isDisabled\":null,\"type\":\"Filter\",\"branchParametersTemplate\":[],\"id\":\"filter 1\"}"""
    val fragment =
      s"""{\"additionalFields\":{\"layoutData\":{\"x\":0,\"y\":180},\"description\":null},\"ref\":{\"id\":\"fragment\",\"parameters\":[],\"outputVariableNames\":{\"output\":\"output\"}},\"isDisabled\":null,\"fragmentParams\":null,\"type\":\"FragmentInput\",\"id\":\"fragment\"}"""
    val choice =
      s"""{
         |  "additionalFields": {
         |    "layoutData": {
         |      "x": -198,
         |      "y": 152
         |    }
         |  },
         |  "expression": null,
         |  "exprVal": null,
         |  "type": "Switch",
         |  "branchParametersTemplate": [],
         |  "id": "choice"
         |}""".stripMargin

    val jsonList = List(enricher, filter, fragment, choice)
    jsonList.foreach(nodeData => schema should validateJson(createJsonFromString(nodeData)))
  }

}
