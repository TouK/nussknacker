import {mapProcessDefinitionToServices} from "../../containers/admin/Services"

jest.mock('../../containers/theme');

describe("translating process structure to services", () => {

  it("should work for real data", () => {
    expect(mapProcessDefinitionToServices(bigInput)).toEqual(bigResult)
  })

  it("translate serviceless processing types to empty array", () => {
    let input = {
      processingType1: {},
      processingType2: {}
    }
    expect(mapProcessDefinitionToServices(input)).toEqual([])
  })

  it("translate simplest possible service definitoin", () => {
    let input = {
      "streaming": {
        "service1": {
          "parameters": [],
          "returnType": {
            "refClazzName": "string"
          },
          "categories": []
        },
      }
    }

    expect(mapProcessDefinitionToServices(input)).toEqual([
      {
        "name": "service1",
        "categories": [],
        "parameters": [],
        "processingType": "streaming"
      }
    ])
  })

  it("map categories", () => {
    let input = {
      "streaming": {
        "service1": {
          "parameters": [],
          "returnType": {
            "refClazzName": "string"
          },
          "categories": ["category1"]
        },
      }
    }
    expect(mapProcessDefinitionToServices(input)).toEqual([
      {
        "name": "service1",
        "categories": ["category1"],
        "parameters": [],
        "processingType": "streaming"
      }
    ])
  })

  it("map parameters", () => {
    let input = {
      "streaming": {
        "service1": {
          "parameters": [
            {
              "name": "foo",
              "typ": {
                "refClazzName": "string"
              }
            }
          ],
          "returnType": {
            "refClazzName": "string"
          },
          "categories": []
        },
      }
    }
    expect(mapProcessDefinitionToServices(input)).toEqual([
      {
        "name": "service1",
        "categories": [],
        "parameters": [
          {
            "name": "foo",
            "typ": {
              "refClazzName": "string"
            }
          }
        ],
        "processingType": "streaming"
      }
    ])
  })

  let bigInput = {
    "streaming": {
      "accountService": {
        "parameters": [],
        "returnType": null,
        "categories": [
          "Category1"
        ]
      },
      "multipleParamsService": {
        "parameters": [
          {
            "name": "foo",
            "typ": {
              "refClazzName": "java.lang.String"
            }
          },
          {
            "name": "bar",
            "typ": {
              "refClazzName": "java.lang.String"
            }
          },
          {
            "name": "baz",
            "typ": {
              "refClazzName": "java.lang.String"
            }
          },
          {
            "name": "quax",
            "typ": {
              "refClazzName": "java.lang.String"
            }
          }
        ],
        "returnType": null,
        "categories": [
          "Category1",
          "Category2"
        ]
      },
      "paramService": {
        "parameters": [
          {
            "name": "param",
            "typ": {
              "refClazzName": "java.lang.String"
            },
            "restriction": {
              "type": "StringValues",
              "values": [
                "a",
                "b",
                "c"
              ]
            }
          }
        ],
        "returnType": {
          "refClazzName": "java.lang.String"
        },
        "categories": [
          "Category1"
        ]
      },
      "serviceModelService": {
        "parameters": [],
        "returnType": null,
        "categories": [
          "Category1",
          "Category2"
        ]
      },
      "componentService": {
        "parameters": [],
        "returnType": null,
        "categories": [
          "Category1",
          "Category2"
        ]
      },
      "enricher": {
        "parameters": [
          {
            "name": "param",
            "typ": {
              "refClazzName": "java.lang.String"
            }
          }
        ],
        "returnType": {
          "refClazzName": "pl.touk.nussknacker.engine.management.sample.RichObject"
        },
        "categories": [
          "Category1",
          "Category2"
        ]
      },
      "transactionService": {
        "parameters": [],
        "returnType": null,
        "categories": [
          "Category1"
        ]
      }
    },
    "request-response": {
      "enricherService": {
        "parameters": [],
        "returnType": {
          "refClazzName": "java.lang.String"
        },
        "categories": [
          "RequestResponseCategory1"
        ]
      },
      "timeMeasuringEnricherService": {
        "parameters": [],
        "returnType": {
          "refClazzName": "java.lang.String"
        },
        "categories": [
          "RequestResponseCategory1"
        ]
      },
      "slowEnricherService": {
        "parameters": [],
        "returnType": {
          "refClazzName": "java.lang.String"
        },
        "categories": [
          "RequestResponseCategory1"
        ]
      },
      "processorService": {
        "parameters": [],
        "returnType": null,
        "categories": [
          "RequestResponseCategory1"
        ]
      }
    }
  }

  let bigResult = [
    {
      "name": "accountService",
      "categories": [
        "Category1"
      ],
      "parameters": [],
      "processingType": "streaming"
    },
    {
      "name": "componentService",
      "categories": [
        "Category1",
        "Category2"
      ],
      "parameters": [],
      "processingType": "streaming"
    },
    {
      "name": "enricher",
      "categories": [
        "Category1",
        "Category2"
      ],
      "parameters": [
        {
          "name": "param",
          "typ": {
            "refClazzName": "java.lang.String"
          }
        }
      ],
      "processingType": "streaming"
    },
    {
      "name": "enricherService",
      "categories": [
        "RequestResponseCategory1"
      ],
      "parameters": [],
      "processingType": "request-response"
    },
    {
      "name": "multipleParamsService",
      "categories": [
        "Category1",
        "Category2"
      ],
      "parameters": [
        {
          "name": "foo",
          "typ": {
            "refClazzName": "java.lang.String"
          }
        },
        {
          "name": "bar",
          "typ": {
            "refClazzName": "java.lang.String"
          }
        },
        {
          "name": "baz",
          "typ": {
            "refClazzName": "java.lang.String"
          }
        },
        {
          "name": "quax",
          "typ": {
            "refClazzName": "java.lang.String"
          }
        }
      ],
      "processingType": "streaming"
    },
    {
      "name": "paramService",
      "categories": [
        "Category1"
      ],
      "parameters": [
        {
          "name": "param",
          "typ": {
            "refClazzName": "java.lang.String"
          },
          "restriction": {
            "type": "StringValues",
            "values": [
              "a",
              "b",
              "c"
            ]
          }

        }
      ],
      "processingType": "streaming"
    },
    {
      "name": "processorService",
      "categories": [
        "RequestResponseCategory1"
      ],
      "parameters": [],
      "processingType": "request-response"
    },
    {
      "name": "serviceModelService",
      "categories": [
        "Category1",
        "Category2"
      ],
      "parameters": [],
      "processingType": "streaming"
    },
    {
      "name": "slowEnricherService",
      "categories": [
        "RequestResponseCategory1"
      ],
      "parameters": [],
      "processingType": "request-response"
    },
    {
      "name": "timeMeasuringEnricherService",
      "categories": [
        "RequestResponseCategory1"
      ],
      "parameters": [],
      "processingType": "request-response"
    },
    {
      "name": "transactionService",
      "categories": [
        "Category1"
      ],
      "parameters": [],
      "processingType": "streaming"
    }
  ]
})
