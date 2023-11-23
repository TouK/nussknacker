package db.migration

import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.CirceUtil

class V1_047__SetFragmentParameterNewFieldsDefaults extends AnyFunSuite with Matchers {

  private def parse(str: String): Json = CirceUtil.decodeJsonUnsafe[Json](str, "Failed to decode")

  private val oldFragment: Json = parse(
    s"""{
       |  "metaData" : {
       |    "id" : "migration_test",
       |    "additionalFields" : {
       |      "description" : null,
       |      "properties" : {
       |        "docsUrl" : ""
       |      },
       |      "metaDataType" : "FragmentSpecificData"
       |    }
       |  },
       |  "nodes" : [
       |    {
       |      "id" : "input",
       |      "parameters" : [
       |        {
       |          "name" : "paramString",
       |          "typ" : {
       |            "refClazzName" : "java.lang.String"
       |          }
       |        },
       |        {
       |          "name" : "paramBool",
       |          "typ" : {
       |            "refClazzName" : "java.lang.Boolean"
       |          }
       |        }
       |      ],
       |      "additionalFields" : {
       |        "description" : "desc",
       |        "layoutData" : {
       |          "x" : 0,
       |          "y" : 0
       |        }
       |      },
       |      "type" : "FragmentInputDefinition"
       |    },
       |    {
       |      "id" : "variable",
       |      "varName" : "varName",
       |      "value" : {
       |        "language" : "spel",
       |        "expression" : "#paramString"
       |      },
       |      "additionalFields" : {
       |        "description" : null,
       |        "layoutData" : {
       |          "x" : 0,
       |          "y" : 180
       |        }
       |      },
       |      "type" : "Variable"
       |    },
       |    {
       |      "id" : "dead-end",
       |      "ref" : {
       |        "typ" : "dead-end",
       |        "parameters" : [
       |        ]
       |      },
       |      "endResult" : null,
       |      "isDisabled" : null,
       |      "additionalFields" : {
       |        "description" : null,
       |        "layoutData" : {
       |          "x" : 0,
       |          "y" : 360
       |        }
       |      },
       |      "type" : "Sink"
       |    }
       |  ],
       |  "additionalBranches" : [
       |  ]
       |}""".stripMargin
  )

  private val newFragment: Json = parse(
    """{
      |  "metaData" : {
      |    "id" : "migration_test",
      |    "additionalFields" : {
      |      "description" : null,
      |      "properties" : {
      |        "docsUrl" : ""
      |      },
      |      "metaDataType" : "FragmentSpecificData"
      |    }
      |  },
      |  "nodes" : [
      |    {
      |      "id" : "input",
      |      "parameters" : [
      |        {
      |          "name" : "paramString",
      |          "typ" : {
      |            "refClazzName" : "java.lang.String"
      |          },
      |          "required" : false,
      |          "initialValue" : null,
      |          "hintText" : null,
      |          "inputConfig" : {
      |            "inputMode" : "InputModeAny",
      |            "fixedValuesType" : null,
      |            "fixedValuesList" : null,
      |            "fixedValuesListPresetId" : null,
      |            "resolvedPresetFixedValuesList" : null
      |          }
      |        },
      |        {
      |          "name" : "paramBool",
      |          "typ" : {
      |            "refClazzName" : "java.lang.Boolean"
      |          },
      |          "required" : false,
      |          "initialValue" : null,
      |          "hintText" : null,
      |          "inputConfig" : {
      |            "inputMode" : "InputModeAny",
      |            "fixedValuesType" : null,
      |            "fixedValuesList" : null,
      |            "fixedValuesListPresetId" : null,
      |            "resolvedPresetFixedValuesList" : null
      |          }
      |        }
      |      ],
      |      "additionalFields" : {
      |        "description" : "desc",
      |        "layoutData" : {
      |          "x" : 0,
      |          "y" : 0
      |        }
      |      },
      |      "type" : "FragmentInputDefinition"
      |    },
      |    {
      |      "id" : "variable",
      |      "varName" : "varName",
      |      "value" : {
      |        "language" : "spel",
      |        "expression" : "#paramString"
      |      },
      |      "additionalFields" : {
      |        "description" : null,
      |        "layoutData" : {
      |          "x" : 0,
      |          "y" : 180
      |        }
      |      },
      |      "type" : "Variable"
      |    },
      |    {
      |      "id" : "dead-end",
      |      "ref" : {
      |        "typ" : "dead-end",
      |        "parameters" : [
      |        ]
      |      },
      |      "endResult" : null,
      |      "isDisabled" : null,
      |      "additionalFields" : {
      |        "description" : null,
      |        "layoutData" : {
      |          "x" : 0,
      |          "y" : 360
      |        }
      |      },
      |      "type" : "Sink"
      |    }
      |  ],
      |  "additionalBranches" : [
      |  ]
      |}""".stripMargin
  )

  test("set absent fragment parameter fields to new defaults") {
    V1_047__SetFragmentParameterNewFieldsDefaultsDefinition.updateProcessJson(oldFragment) shouldBe Some(newFragment)
  }

}
