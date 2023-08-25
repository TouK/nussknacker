package db.migration

import db.migration.V1_046__RenameSubprocessToFragmentDefinition.updateProcessJson
import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.CirceUtil

class V1_046__RenameSubprocessToFragmentDefinitionSpec extends AnyFunSuite with Matchers {

  private def parse(str: String): Json = CirceUtil.decodeJsonUnsafe[Json](str, "Failed to decode")

  private val subprc: Json = parse(
    s"""{
       |    "nodes": [
       |        {
       |            "id": "postGenericSource",
       |            "ref": {
       |                "typ": "post-generic-source",
       |                "parameters": []
       |            },
       |            "additionalFields": {},
       |            "type": "Source"
       |        },
       |        {
       |            "outputs": {
       |                "nbaMonitorTemplate": [
       |                    {
       |                        "nextFalse": [
       |                            {
       |                                "outputs": {
       |                                    "output": [
       |                                        {
       |                                            "id": "node",
       |                                            "service": {
       |                                                "id": "node",
       |                                                "parameters": []
       |                                            },
       |                                            "output": "node",
       |                                            "additionalFields": {},
       |                                            "type": "Enricher"
       |                                        },
       |                                        {
       |                                            "outputs": {
       |                                                "output": [
       |                                                    {
       |                                                        "outputs": {},
       |                                                        "id": "node",
       |                                                        "ref": {
       |                                                            "id": "node",
       |                                                            "parameters": [],
       |                                                            "outputVariableNames": {}
       |                                                        },
       |                                                        "additionalFields": {},
       |                                                        "subprocessParams": null,
       |                                                        "type": "SubprocessInput"
       |                                                    }
       |                                                ]
       |                                            },
       |                                            "id": "node",
       |                                            "ref": {
       |                                                "id": "node",
       |                                                "parameters": [],
       |                                                "outputVariableNames": {}
       |                                            },
       |                                            "additionalFields": {},
       |                                            "subprocessParams": null,
       |                                            "type": "SubprocessInput"
       |                                        }
       |                                    ]
       |                                },
       |                                "id": "node",
       |                                "ref": {
       |                                    "id": "node",
       |                                    "parameters": [],
       |                                    "outputVariableNames": {}
       |                                },
       |                                "additionalFields": {},
       |                                "subprocessParams": null,
       |                                "type": "SubprocessInput"
       |                            }
       |                        ],
       |                        "id": "filter",
       |                        "expression": {
       |                            "language": "spel",
       |                            "expression": ""
       |                        },
       |                        "additionalFields": {},
       |                        "type": "Filter"
       |                    }
       |                ]
       |            },
       |            "id": "frag",
       |            "ref": {
       |                "id": "frag",
       |                "parameters": [ ],
       |                "outputVariableNames": {}
       |            },
       |            "additionalFields": {},
       |            "type": "SubprocessInput",
       |            "subprocessParams": null
       |        }
       |    ],
       |    "additionalBranches": []
       |}
       |""".stripMargin
  )

  private val frag: Json = parse(
    """{
      |    "nodes": [
      |        {
      |            "id": "postGenericSource",
      |            "ref": {
      |                "typ": "post-generic-source",
      |                "parameters": []
      |            },
      |            "additionalFields": {},
      |            "type": "Source"
      |        },
      |        {
      |            "outputs": {
      |                "nbaMonitorTemplate": [
      |                    {
      |                        "nextFalse": [
      |                            {
      |                                "outputs": {
      |                                    "output": [
      |                                        {
      |                                            "id": "node",
      |                                            "service": {
      |                                                "id": "node",
      |                                                "parameters": []
      |                                            },
      |                                            "output": "node",
      |                                            "additionalFields": {},
      |                                            "type": "Enricher"
      |                                        },
      |                                        {
      |                                            "outputs": {
      |                                                "output": [
      |                                                    {
      |                                                        "outputs": {},
      |                                                        "id": "node",
      |                                                        "ref": {
      |                                                            "id": "node",
      |                                                            "parameters": [],
      |                                                            "outputVariableNames": {}
      |                                                        },
      |                                                        "additionalFields": {},
      |                                                        "type": "FragmentInput",
      |                                                        "fragmentParams": null
      |                                                    }
      |                                                ]
      |                                            },
      |                                            "id": "node",
      |                                            "ref": {
      |                                                "id": "node",
      |                                                "parameters": [],
      |                                                "outputVariableNames": {}
      |                                            },
      |                                            "additionalFields": {},
      |                                            "type": "FragmentInput",
      |                                            "fragmentParams": null
      |                                        }
      |                                    ]
      |                                },
      |                                "id": "node",
      |                                "ref": {
      |                                    "id": "node",
      |                                    "parameters": [],
      |                                    "outputVariableNames": {}
      |                                },
      |                                "additionalFields": {},
      |                                "type": "FragmentInput",
      |                                "fragmentParams": null
      |                            }
      |                        ],
      |                        "id": "filter",
      |                        "expression": {
      |                            "language": "spel",
      |                            "expression": ""
      |                        },
      |                        "additionalFields": {},
      |                        "type": "Filter"
      |                    }
      |                ]
      |            },
      |            "id": "frag",
      |            "ref": {
      |                "id": "frag",
      |                "parameters": [ ],
      |                "outputVariableNames": {}
      |            },
      |            "additionalFields": {},
      |            "type": "FragmentInput",
      |            "fragmentParams": null
      |        }
      |    ],
      |    "additionalBranches": []
      |}
      |""".stripMargin
  )

  test("migrate subprocess to fragment") {
    updateProcessJson(subprc) shouldBe Some(frag)
  }

}
