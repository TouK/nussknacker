import * as JsonUtils from "../common/JsonUtils"

describe("json utils", () => {
  it("should flatten object #1", () => {
    const obj = {}
    expect(JsonUtils.flattenObj(obj)).toEqual({})
  })

  it("should flatten object #2", () => {
    const obj = {
      "service": {
        "parameters": [
          {"expression": {"expression": "'foo'"}},
          {"expression": {"expression": "'bar'"}}
        ]
      }
    }
    expect(JsonUtils.flattenObj(obj)).toEqual({
        "service.parameters[0].expression.expression": "'foo'",
        "service.parameters[1].expression.expression": "'bar'"
      }
    )
  })

  it("should diff objects", () => {
    const obj1 = {
      "service": {
        "parameters": [
          {"expression": {"expression": "'foo'"}},
          {"expression": {"expression": "'bar'"}}
        ]
      }
    }
    const obj2 = {
      "service": {
        "parameters": [
          {"expression": {"expression": "'foo2'"}},
          {"expression": {"expression": "'bar'"}}
        ]
      }
    }

    expect(JsonUtils.objectDiff(obj1, obj2)).toEqual({
      "service": {
        "parameters": [
          {"expression": {"expression": "'foo'"}}
        ]
      }
    })
  })

  it("should work with empty objects/properties", () => {
    expect(JsonUtils.objectDiff({"foo": null}, null)).toEqual({})
    expect(JsonUtils.objectDiff(null, {"foo": null})).toEqual({})
    expect(JsonUtils.objectDiff({"foo": null}, null)).toEqual({})
  })

})