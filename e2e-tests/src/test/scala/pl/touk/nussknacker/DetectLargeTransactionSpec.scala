package pl.touk.nussknacker

import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Slow
import pl.touk.nussknacker.config.WithE2EInstallationExampleRestAssuredUsersExtensions
import pl.touk.nussknacker.test.{NuRestAssureExtensions, NuRestAssureMatchers, VeryPatientScalaFutures}

@Slow
class DetectLargeTransactionSpec
    extends AnyFreeSpecLike
    with NuRestAssureExtensions
    with NuRestAssureMatchers
    with WithE2EInstallationExampleRestAssuredUsersExtensions
    with BaseE2ESpec
    with Matchers
    with VeryPatientScalaFutures {

  private val designerServiceUrl = "http://localhost:8080"

  "Large transactions should be properly detected" in {
    val smallAmountTransactions = List(
      transactionJson(amount = 1),
      transactionJson(amount = 2),
      transactionJson(amount = 3),
    )
    val largeAmountTransactions = List(
      transactionJson(amount = 100),
      transactionJson(amount = 1000),
      transactionJson(amount = 10000),
    )

    (smallAmountTransactions ::: largeAmountTransactions).foreach { transaction =>
      client.sendMessageToKafka("Transactions", transaction)
    }

    eventually {
      val processedTransactions = client.readAllMessagesFromKafka("ProcessedTransactions")
      processedTransactions should equal(largeAmountTransactions)
      given()
        .when()
        .request()
        .basicAuthAdmin()
        .get(
          s"$designerServiceUrl/api/liveData/DetectLargeTransactions"
        )
        .Then()
        .statusCode(200)
        .matchJsonWithRegexValuesBody(
          ignoreOrderOfElementsInArrays = true,
          json = s"""
             |{
             |  "timestamp": "${regexes.zuluDateRegex}",
             |  "results": {
             |    "nodeResults": null,
             |    "nodeTransitionResults": [
             |      {
             |        "sourceNodeId": "only large ones",
             |        "destinationNodeId": null,
             |        "results": [
             |          {
             |            "id": "DetectLargeTransactions-transactions-0-0",
             |            "cid": {
             |              "nid": "transactions",
             |              "tid": 0,
             |              "idx": 0,
             |              "path": []
             |            },
             |            "timestamp": "${regexes.zuluDateRegex}",
             |            "variables": {
             |              "input": {
             |                "pretty": {
             |                  "amount": 1,
             |                  "clientId": "100",
             |                  "isLast": false
             |                }
             |              },
             |              "inputMeta": {
             |                "pretty": {
             |                  "timestamp": "${regexes.decimalRegex}",
             |                  "partition": 0,
             |                  "timestampType": "CREATE_TIME",
             |                  "key": "",
             |                  "offset": 0,
             |                  "leaderEpoch": 0,
             |                  "topic": "Transactions",
             |                  "headers": {}
             |                }
             |              }
             |            }
             |          },
             |          {
             |            "id": "DetectLargeTransactions-transactions-0-1",
             |            "cid": {
             |              "nid": "transactions",
             |              "tid": 0,
             |              "idx": 1,
             |              "path": []
             |            },
             |            "timestamp": "${regexes.zuluDateRegex}",
             |            "variables": {
             |              "input": {
             |                "pretty": {
             |                  "amount": 2,
             |                  "clientId": "100",
             |                  "isLast": false
             |                }
             |              },
             |              "inputMeta": {
             |                "pretty": {
             |                  "timestamp": "${regexes.decimalRegex}",
             |                  "partition": 0,
             |                  "timestampType": "CREATE_TIME",
             |                  "key": "",
             |                  "offset": 1,
             |                  "leaderEpoch": 0,
             |                  "topic": "Transactions",
             |                  "headers": {}
             |                }
             |              }
             |            }
             |          },
             |          {
             |            "id": "DetectLargeTransactions-transactions-0-2",
             |            "cid": {
             |              "nid": "transactions",
             |              "tid": 0,
             |              "idx": 2,
             |              "path": []
             |            },
             |            "timestamp": "${regexes.zuluDateRegex}",
             |            "variables": {
             |              "input": {
             |                "pretty": {
             |                  "amount": 3,
             |                  "clientId": "100",
             |                  "isLast": false
             |                }
             |              },
             |              "inputMeta": {
             |                "pretty": {
             |                  "timestamp": "${regexes.decimalRegex}",
             |                  "partition": 0,
             |                  "timestampType": "CREATE_TIME",
             |                  "key": "",
             |                  "offset": 2,
             |                  "leaderEpoch": 0,
             |                  "topic": "Transactions",
             |                  "headers": {}
             |                }
             |              }
             |            }
             |          }
             |        ],
             |        "totalCount": 3,
             |        "currentThroughput": "${regexes.decimalRegex}"
             |      },
             |      {
             |        "sourceNodeId": "only large ones",
             |        "destinationNodeId": "send for audit",
             |        "results": [
             |          {
             |            "id": "DetectLargeTransactions-transactions-0-3",
             |            "cid": {
             |              "nid": "transactions",
             |              "tid": 0,
             |              "idx": 3,
             |              "path": []
             |            },
             |            "timestamp": "${regexes.zuluDateRegex}",
             |            "variables": {
             |              "input": {
             |                "pretty": {
             |                  "amount": 100,
             |                  "clientId": "100",
             |                  "isLast": false
             |                }
             |              },
             |              "inputMeta": {
             |                "pretty": {
             |                  "timestamp": "${regexes.decimalRegex}",
             |                  "partition": 0,
             |                  "timestampType": "CREATE_TIME",
             |                  "key": "",
             |                  "offset": 3,
             |                  "leaderEpoch": 0,
             |                  "topic": "Transactions",
             |                  "headers": {}
             |                }
             |              }
             |            }
             |          },
             |          {
             |            "id": "DetectLargeTransactions-transactions-0-4",
             |            "cid": {
             |              "nid": "transactions",
             |              "tid": 0,
             |              "idx": 4,
             |              "path": []
             |            },
             |            "timestamp": "${regexes.zuluDateRegex}",
             |            "variables": {
             |              "input": {
             |                "pretty": {
             |                  "amount": 1000,
             |                  "clientId": "100",
             |                  "isLast": false
             |                }
             |              },
             |              "inputMeta": {
             |                "pretty": {
             |                  "timestamp": "${regexes.decimalRegex}",
             |                  "partition": 0,
             |                  "timestampType": "CREATE_TIME",
             |                  "key": "",
             |                  "offset": 4,
             |                  "leaderEpoch": 0,
             |                  "topic": "Transactions",
             |                  "headers": {}
             |                }
             |              }
             |            }
             |          },
             |          {
             |            "id": "DetectLargeTransactions-transactions-0-5",
             |            "cid": {
             |              "nid": "transactions",
             |              "tid": 0,
             |              "idx": 5,
             |              "path": []
             |            },
             |            "timestamp": "${regexes.zuluDateRegex}",
             |            "variables": {
             |              "input": {
             |                "pretty": {
             |                  "amount": 10000,
             |                  "clientId": "100",
             |                  "isLast": false
             |                }
             |              },
             |              "inputMeta": {
             |                "pretty": {
             |                  "timestamp": "${regexes.decimalRegex}",
             |                  "partition": 0,
             |                  "timestampType": "CREATE_TIME",
             |                  "key": "",
             |                  "offset": 5,
             |                  "leaderEpoch": 0,
             |                  "topic": "Transactions",
             |                  "headers": {}
             |                }
             |              }
             |            }
             |          }
             |        ],
             |        "totalCount": 3,
             |        "currentThroughput": "${regexes.decimalRegex}"
             |      },
             |      {
             |        "sourceNodeId": "send for audit",
             |        "destinationNodeId": null,
             |        "results": [
             |          {
             |            "id": "DetectLargeTransactions-transactions-0-3",
             |            "cid": {
             |              "nid": "transactions",
             |              "tid": 0,
             |              "idx": 3,
             |              "path": []
             |            },
             |            "timestamp": "${regexes.zuluDateRegex}",
             |            "variables": {
             |              "send for audit": {
             |                "pretty": {
             |                  "key": null,
             |                  "value": {
             |                    "amount": 100,
             |                    "clientId": "100",
             |                    "isLast": false
             |                  }
             |                }
             |              }
             |            }
             |          },
             |          {
             |            "id": "DetectLargeTransactions-transactions-0-4",
             |            "cid": {
             |              "nid": "transactions",
             |              "tid": 0,
             |              "idx": 4,
             |              "path": []
             |            },
             |            "timestamp": "${regexes.zuluDateRegex}",
             |            "variables": {
             |              "send for audit": {
             |                "pretty": {
             |                  "key": null,
             |                  "value": {
             |                    "amount": 1000,
             |                    "clientId": "100",
             |                    "isLast": false
             |                  }
             |                }
             |              }
             |            }
             |          },
             |          {
             |            "id": "DetectLargeTransactions-transactions-0-5",
             |            "cid": {
             |              "nid": "transactions",
             |              "tid": 0,
             |              "idx": 5,
             |              "path": []
             |            },
             |            "timestamp": "${regexes.zuluDateRegex}",
             |            "variables": {
             |              "send for audit": {
             |                "pretty": {
             |                  "key": null,
             |                  "value": {
             |                    "amount": 10000,
             |                    "clientId": "100",
             |                    "isLast": false
             |                  }
             |                }
             |              }
             |            }
             |          }
             |        ],
             |        "totalCount": 3
             |      },
             |      {
             |        "sourceNodeId": "transactions",
             |        "destinationNodeId": "only large ones",
             |        "results": [
             |          {
             |            "id": "DetectLargeTransactions-transactions-0-0",
             |            "cid": {
             |              "nid": "transactions",
             |              "tid": 0,
             |              "idx": 0,
             |              "path": []
             |            },
             |            "timestamp": "${regexes.zuluDateRegex}",
             |            "variables": {
             |              "input": {
             |                "pretty": {
             |                  "amount": 1,
             |                  "clientId": "100",
             |                  "isLast": false
             |                }
             |              },
             |              "inputMeta": {
             |                "pretty": {
             |                  "timestamp": "${regexes.decimalRegex}",
             |                  "partition": 0,
             |                  "timestampType": "CREATE_TIME",
             |                  "key": "",
             |                  "offset": 0,
             |                  "leaderEpoch": 0,
             |                  "topic": "Transactions",
             |                  "headers": {}
             |                }
             |              }
             |            }
             |          },
             |          {
             |            "id": "DetectLargeTransactions-transactions-0-1",
             |            "cid": {
             |              "nid": "transactions",
             |              "tid": 0,
             |              "idx": 1,
             |              "path": []
             |            },
             |            "timestamp": "${regexes.zuluDateRegex}",
             |            "variables": {
             |              "input": {
             |                "pretty": {
             |                  "amount": 2,
             |                  "clientId": "100",
             |                  "isLast": false
             |                }
             |              },
             |              "inputMeta": {
             |                "pretty": {
             |                  "timestamp": "${regexes.decimalRegex}",
             |                  "partition": 0,
             |                  "timestampType": "CREATE_TIME",
             |                  "key": "",
             |                  "offset": 1,
             |                  "leaderEpoch": 0,
             |                  "topic": "Transactions",
             |                  "headers": {}
             |                }
             |              }
             |            }
             |          },
             |          {
             |            "id": "DetectLargeTransactions-transactions-0-2",
             |            "cid": {
             |              "nid": "transactions",
             |              "tid": 0,
             |              "idx": 2,
             |              "path": []
             |            },
             |            "timestamp": "${regexes.zuluDateRegex}",
             |            "variables": {
             |              "input": {
             |                "pretty": {
             |                  "amount": 3,
             |                  "clientId": "100",
             |                  "isLast": false
             |                }
             |              },
             |              "inputMeta": {
             |                "pretty": {
             |                  "timestamp": "${regexes.decimalRegex}",
             |                  "partition": 0,
             |                  "timestampType": "CREATE_TIME",
             |                  "key": "",
             |                  "offset": 2,
             |                  "leaderEpoch": 0,
             |                  "topic": "Transactions",
             |                  "headers": {}
             |                }
             |              }
             |            }
             |          },
             |          {
             |            "id": "DetectLargeTransactions-transactions-0-3",
             |            "cid": {
             |              "nid": "transactions",
             |              "tid": 0,
             |              "idx": 3,
             |              "path": []
             |            },
             |            "timestamp": "${regexes.zuluDateRegex}",
             |            "variables": {
             |              "input": {
             |                "pretty": {
             |                  "amount": 100,
             |                  "clientId": "100",
             |                  "isLast": false
             |                }
             |              },
             |              "inputMeta": {
             |                "pretty": {
             |                  "timestamp": "${regexes.decimalRegex}",
             |                  "partition": 0,
             |                  "timestampType": "CREATE_TIME",
             |                  "key": "",
             |                  "offset": 3,
             |                  "leaderEpoch": 0,
             |                  "topic": "Transactions",
             |                  "headers": {}
             |                }
             |              }
             |            }
             |          },
             |          {
             |            "id": "DetectLargeTransactions-transactions-0-4",
             |            "cid": {
             |              "nid": "transactions",
             |              "tid": 0,
             |              "idx": 4,
             |              "path": []
             |            },
             |            "timestamp": "${regexes.zuluDateRegex}",
             |            "variables": {
             |              "input": {
             |                "pretty": {
             |                  "amount": 1000,
             |                  "clientId": "100",
             |                  "isLast": false
             |                }
             |              },
             |              "inputMeta": {
             |                "pretty": {
             |                  "timestamp": "${regexes.decimalRegex}",
             |                  "partition": 0,
             |                  "timestampType": "CREATE_TIME",
             |                  "key": "",
             |                  "offset": 4,
             |                  "leaderEpoch": 0,
             |                  "topic": "Transactions",
             |                  "headers": {}
             |                }
             |              }
             |            }
             |          },
             |          {
             |            "id": "DetectLargeTransactions-transactions-0-5",
             |            "cid": {
             |              "nid": "transactions",
             |              "tid": 0,
             |              "idx": 5,
             |              "path": []
             |            },
             |            "timestamp": "${regexes.zuluDateRegex}",
             |            "variables": {
             |              "input": {
             |                "pretty": {
             |                  "amount": 10000,
             |                  "clientId": "100",
             |                  "isLast": false
             |                }
             |              },
             |              "inputMeta": {
             |                "pretty": {
             |                  "timestamp": "${regexes.decimalRegex}",
             |                  "partition": 0,
             |                  "timestampType": "CREATE_TIME",
             |                  "key": "",
             |                  "offset": 5,
             |                  "leaderEpoch": 0,
             |                  "topic": "Transactions",
             |                  "headers": {}
             |                }
             |              }
             |            }
             |          }
             |        ],
             |        "totalCount": 6,
             |        "currentThroughput": "${regexes.decimalRegex}"
             |      }
             |    ],
             |    "expressionEvaluationResults": {
             |      "only large ones": [
             |        {
             |          "contextId": "DetectLargeTransactions-transactions-0-0",
             |          "cid": {
             |            "nid": "transactions",
             |            "tid": 0,
             |            "idx": 0,
             |            "path": []
             |          },
             |          "timestamp": "${regexes.zuluDateRegex}",
             |          "name": "expression",
             |          "value": {
             |            "pretty": false
             |          }
             |        },
             |        {
             |          "contextId": "DetectLargeTransactions-transactions-0-1",
             |          "cid": {
             |            "nid": "transactions",
             |            "tid": 0,
             |            "idx": 1,
             |            "path": []
             |          },
             |          "timestamp": "${regexes.zuluDateRegex}",
             |          "name": "expression",
             |          "value": {
             |            "pretty": false
             |          }
             |        },
             |        {
             |          "contextId": "DetectLargeTransactions-transactions-0-2",
             |          "cid": {
             |            "nid": "transactions",
             |            "tid": 0,
             |            "idx": 2,
             |            "path": []
             |          },
             |          "timestamp": "${regexes.zuluDateRegex}",
             |          "name": "expression",
             |          "value": {
             |            "pretty": false
             |          }
             |        },
             |        {
             |          "contextId": "DetectLargeTransactions-transactions-0-3",
             |          "cid": {
             |            "nid": "transactions",
             |            "tid": 0,
             |            "idx": 3,
             |            "path": []
             |          },
             |          "timestamp": "${regexes.zuluDateRegex}",
             |          "name": "expression",
             |          "value": {
             |            "pretty": true
             |          }
             |        },
             |        {
             |          "contextId": "DetectLargeTransactions-transactions-0-4",
             |          "cid": {
             |            "nid": "transactions",
             |            "tid": 0,
             |            "idx": 4,
             |            "path": []
             |          },
             |          "timestamp": "${regexes.zuluDateRegex}",
             |          "name": "expression",
             |          "value": {
             |            "pretty": true
             |          }
             |        },
             |        {
             |          "contextId": "DetectLargeTransactions-transactions-0-5",
             |          "cid": {
             |            "nid": "transactions",
             |            "tid": 0,
             |            "idx": 5,
             |            "path": []
             |          },
             |          "timestamp": "${regexes.zuluDateRegex}",
             |          "name": "expression",
             |          "value": {
             |            "pretty": true
             |          }
             |        }
             |      ],
             |      "send for audit": [
             |        {
             |          "contextId": "DetectLargeTransactions-transactions-0-3",
             |          "cid": {
             |            "nid": "transactions",
             |            "tid": 0,
             |            "idx": 3,
             |            "path": []
             |          },
             |          "timestamp": "${regexes.zuluDateRegex}",
             |          "name": "Value",
             |          "value": {
             |            "pretty": {
             |              "amount": 100,
             |              "clientId": "100",
             |              "isLast": false
             |            }
             |          }
             |        },
             |        {
             |          "contextId": "DetectLargeTransactions-transactions-0-3",
             |          "cid": {
             |            "nid": "transactions",
             |            "tid": 0,
             |            "idx": 3,
             |            "path": []
             |          },
             |          "timestamp": "${regexes.zuluDateRegex}",
             |          "name": "Key",
             |          "value": null
             |        },
             |        {
             |          "contextId": "DetectLargeTransactions-transactions-0-4",
             |          "cid": {
             |            "nid": "transactions",
             |            "tid": 0,
             |            "idx": 4,
             |            "path": []
             |          },
             |          "timestamp": "${regexes.zuluDateRegex}",
             |          "name": "Value",
             |          "value": {
             |            "pretty": {
             |              "amount": 1000,
             |              "clientId": "100",
             |              "isLast": false
             |            }
             |          }
             |        },
             |        {
             |          "contextId": "DetectLargeTransactions-transactions-0-4",
             |          "cid": {
             |            "nid": "transactions",
             |            "tid": 0,
             |            "idx": 4,
             |            "path": []
             |          },
             |          "timestamp": "${regexes.zuluDateRegex}",
             |          "name": "Key",
             |          "value": null
             |        },
             |        {
             |          "contextId": "DetectLargeTransactions-transactions-0-5",
             |          "cid": {
             |            "nid": "transactions",
             |            "tid": 0,
             |            "idx": 5,
             |            "path": []
             |          },
             |          "timestamp": "${regexes.zuluDateRegex}",
             |          "name": "Value",
             |          "value": {
             |            "pretty": {
             |              "amount": 10000,
             |              "clientId": "100",
             |              "isLast": false
             |            }
             |          }
             |        },
             |        {
             |          "contextId": "DetectLargeTransactions-transactions-0-5",
             |          "cid": {
             |            "nid": "transactions",
             |            "tid": 0,
             |            "idx": 5,
             |            "path": []
             |          },
             |          "timestamp": "${regexes.zuluDateRegex}",
             |          "name": "Key",
             |          "value": null
             |        }
             |      ],
             |      "transactions": [
             |        {
             |          "contextId": "DetectLargeTransactions-transactions-0-0",
             |          "cid": {
             |            "nid": "transactions",
             |            "tid": 0,
             |            "idx": 0,
             |            "path": []
             |          },
             |          "timestamp": "${regexes.zuluDateRegex}",
             |          "name": "Event time",
             |          "value": {
             |            "pretty": "${regexes.zuluDateRegex}"
             |          }
             |        },
             |        {
             |          "contextId": "DetectLargeTransactions-transactions-0-1",
             |          "cid": {
             |            "nid": "transactions",
             |            "tid": 0,
             |            "idx": 1,
             |            "path": []
             |          },
             |          "timestamp": "${regexes.zuluDateRegex}",
             |          "name": "Event time",
             |          "value": {
             |            "pretty": "${regexes.zuluDateRegex}"
             |          }
             |        },
             |        {
             |          "contextId": "DetectLargeTransactions-transactions-0-2",
             |          "cid": {
             |            "nid": "transactions",
             |            "tid": 0,
             |            "idx": 2,
             |            "path": []
             |          },
             |          "timestamp": "${regexes.zuluDateRegex}",
             |          "name": "Event time",
             |          "value": {
             |            "pretty": "${regexes.zuluDateRegex}"
             |          }
             |        },
             |        {
             |          "contextId": "DetectLargeTransactions-transactions-0-3",
             |          "cid": {
             |            "nid": "transactions",
             |            "tid": 0,
             |            "idx": 3,
             |            "path": []
             |          },
             |          "timestamp": "${regexes.zuluDateRegex}",
             |          "name": "Event time",
             |          "value": {
             |            "pretty": "${regexes.zuluDateRegex}"
             |          }
             |        },
             |        {
             |          "contextId": "DetectLargeTransactions-transactions-0-4",
             |          "cid": {
             |            "nid": "transactions",
             |            "tid": 0,
             |            "idx": 4,
             |            "path": []
             |          },
             |          "timestamp": "${regexes.zuluDateRegex}",
             |          "name": "Event time",
             |          "value": {
             |            "pretty": "${regexes.zuluDateRegex}"
             |          }
             |        },
             |        {
             |          "contextId": "DetectLargeTransactions-transactions-0-5",
             |          "cid": {
             |            "nid": "transactions",
             |            "tid": 0,
             |            "idx": 5,
             |            "path": []
             |          },
             |          "timestamp": "${regexes.zuluDateRegex}",
             |          "name": "Event time",
             |          "value": {
             |            "pretty": "${regexes.zuluDateRegex}"
             |          }
             |        }
             |      ]
             |    },
             |    "externalServiceInvocationResults": {
             |      "send for audit": [
             |        {
             |          "contextId": "DetectLargeTransactions-transactions-0-3",
             |          "cid": {
             |            "nid": "transactions",
             |            "tid": 0,
             |            "idx": 3,
             |            "path": []
             |          },
             |          "timestamp": "${regexes.zuluDateRegex}",
             |          "name": "send for audit",
             |          "value": {
             |            "pretty": {
             |              "key": null,
             |              "value": {
             |                "clientId": "100",
             |                "amount": 100,
             |                "isLast": false
             |              }
             |            }
             |          }
             |        },
             |        {
             |          "contextId": "DetectLargeTransactions-transactions-0-4",
             |          "cid": {
             |            "nid": "transactions",
             |            "tid": 0,
             |            "idx": 4,
             |            "path": []
             |          },
             |          "timestamp": "${regexes.zuluDateRegex}",
             |          "name": "send for audit",
             |          "value": {
             |            "pretty": {
             |              "key": null,
             |              "value": {
             |                "clientId": "100",
             |                "amount": 1000,
             |                "isLast": false
             |              }
             |            }
             |          }
             |        },
             |        {
             |          "contextId": "DetectLargeTransactions-transactions-0-5",
             |          "cid": {
             |            "nid": "transactions",
             |            "tid": 0,
             |            "idx": 5,
             |            "path": []
             |          },
             |          "timestamp": "${regexes.zuluDateRegex}",
             |          "name": "send for audit",
             |          "value": {
             |            "pretty": {
             |              "key": null,
             |              "value": {
             |                "clientId": "100",
             |                "amount": 10000,
             |                "isLast": false
             |              }
             |            }
             |          }
             |        }
             |      ]
             |    },
             |    "exceptions": [],
             |    "exceptionsByNodeId": {}
             |  },
             |  "counts": {
             |    "transactions": {
             |      "all": 6,
             |      "errors": 0,
             |      "fragmentCounts": {}
             |    },
             |    "only large ones": {
             |      "all": 6,
             |      "errors": 0,
             |      "fragmentCounts": {}
             |    },
             |    "send for audit": {
             |      "all": 3,
             |      "errors": 0,
             |      "fragmentCounts": {}
             |    }
             |  },
             |  "assertionsResults": {}
             |}""".stripMargin
        )
    }
  }

  override protected def afterEach(): Unit = {
    client.purgeKafkaTopic("Transactions")
    client.purgeKafkaTopic("ProcessedTransactions")
    super.afterEach()
  }

  private def transactionJson(amount: Int) =
    ujson.Obj("clientId" -> "100", "amount" -> amount, "isLast" -> false)
}
