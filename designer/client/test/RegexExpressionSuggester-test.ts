import {RegexExpressionSuggester} from "../src/components/graph/node-modal/editors/expression/ExpressionSuggester";
import {map} from "lodash";
import {expect, describe, it} from '@jest/globals';
import {ClassDefinition} from "../src/types";

const typesInformation: ClassDefinition[] = [
  {
    "clazzName": {"refClazzName": "org.A", type:"", display: "", fields: {}, params: []},
    "methods": {"fooString": {"refClazz": {"refClazzName": "java.lang.String"} }, "barB": {"refClazz": {"refClazzName": "org.B"} } }
  },
  {
    "clazzName": {"refClazzName": "org.B", type:"", display: "", fields: {}, params: []},
    "methods": {"bazC": {"refClazz": {"refClazzName": "org.C"} } }
  },
  {
    "clazzName": {"refClazzName": "org.C", type:"", display: "", fields: {}, params: []},
    "methods": {"quaxString": {"refClazz": {"refClazzName": "java.lang.String"} }}
  },
  {
    "clazzName": {"refClazzName": "org.AA", type:"", display: "", fields: {}, params: []},
    "methods": {"fooString": {"refClazz": {"refClazzName": "java.lang.String"} }, "barB": {"refClazz": {"refClazzName": "org.C"} } }
  },
  {
    "clazzName": {"refClazzName": "org.WithList", type:"", display: "", fields: {}, params: []},
    "methods": {"listField": {"refClazz": {"refClazzName": "java.util.List", params: [{refClazzName: "org.A"}]} }}
  },
  {
    "clazzName": {"refClazzName": "java.lang.String", type:"", display: "", fields: {}, params: []},
    "methods": {"toUpperCase": {"refClazz": {"refClazzName": "java.lang.String"} }}
  },
  {
    "clazzName": {"refClazzName": "java.util.LocalDateTime", type:"", display: "", fields: {}, params: []},
    "methods": {"isBefore": {"refClazz": {"refClazzName": "java.lang.Boolean"}, "params": {"name": "arg0", "refClazz": "java.util.LocalDateTime"}}}
  },
  {
    "clazzName": {"refClazzName": "org.Util", type:"", display: "", fields: {}, params: []},
    "methods": {"now": {"refClazz": {"refClazzName": "java.util.LocalDateTime"}}}
  }
];

const variables = {
  "input": {refClazzName: "org.A"},
  "other": {refClazzName: "org.C"},
  "ANOTHER": {refClazzName: "org.A"},
  "dynamicMap": {refClazzName: "java.util.Map", fields: {'intField': {refClazzName: 'java.lang.Integer'}, 'aField': {refClazzName: "org.A"}} },
  "listVar": {refClazzName: "org.WithList" },
  "util": {refClazzName: "org.Util"},
  "union": {union: [
      {refClazzName: "org.A"},
      {refClazzName: "org.B"},
      {refClazzName: "org.AA"}]},
  "unionOfLists": {union: [
      {"refClazz": "java.util.List", params: [{refClazzName: "org.A"}]},
      {"refClazz": "java.util.List", params: [{refClazzName: "org.B"}]}
    ]},
  "dict": {dict: {
    id: "fooDict",
    valueType: {refClazzName: "org.A"}
  }}
};

const suggestionsFor = (inputValue, caretPosition2d?, stubbedDictSuggestions?) => {
  const _caretPosition2d = caretPosition2d ? caretPosition2d : { row: 0, column: inputValue.length }
  const stubService = {
    fetchDictLabelSuggestions(processingType, dictId, labelPattern): Promise<any> {
      return new Promise(resolve => resolve({ data: stubbedDictSuggestions }))
    }
  }
  const expressionSuggester = new RegexExpressionSuggester(typesInformation, variables, "fooProcessingType", stubService)
  return expressionSuggester.suggestionsFor(inputValue, _caretPosition2d)
}

describe("expression suggester", () => {
  it("should not suggest anything for empty input", () => {
    return expect(suggestionsFor("")).resolves.toMatchSnapshot()
  })

  it("should suggest all global variables if # specified", () => {
    return expect(suggestionsFor("#")).resolves.toMatchSnapshot()
  })

  it("should suggest all global variables if # specified (multiline)", () => {
    return expect(suggestionsFor(`asdasd\n
    #\n
    dsadasdas`, {row: 1, column: 1})).resolves.toMatchSnapshot()
  })

  it("should filter global variables suggestions", () => {
    return expect(suggestionsFor("#ot")).resolves.toMatchSnapshot()
  })

  it("should filter uppercase global variables suggestions", () => {
    return expect(suggestionsFor("#ANO")).resolves.toMatchSnapshot()
  })

  it("should suggest global variable", () => {
    return expect(suggestionsFor("#inpu")).resolves.toMatchSnapshot()
  })

  it("should suggest global variable methods", () => {
    return expect(suggestionsFor("#input.")).resolves.toMatchSnapshot()
  })

  it("should suggest dict variable methods", () => {
    let stubbedDictSuggestions = [
      { key: "one", label: "One" },
      { key: "two", label: "Two" }
    ]
    return expect(suggestionsFor("#dict.", null, stubbedDictSuggestions)).resolves.toMatchSnapshot()
  })


  it("should suggest dict variable methods using indexer syntax", () => {
    let stubbedDictSuggestions = [
      { key: "sentence-with-spaces-and-dots", label: "Sentence with spaces and . dots" }
    ]
    const correctInputs = ["#dict['", "#dict['S", "#dict['Sentence w", "#dict['Sentence with spaces and . dots"]
    map(correctInputs, inputValue => {
      return expect(suggestionsFor(inputValue, null, stubbedDictSuggestions)).resolves.toMatchSnapshot()
    })
  })

  it("should suggest filtered global variable methods", () => {
    return expect(suggestionsFor("#input.fo")).resolves.toMatchSnapshot()
  })

  it("should suggest filtered global variable methods based not on beginning of the method", () => {
    return expect(suggestionsFor("#input.string")).resolves.toMatchSnapshot()
  })

  it("should suggest methods for object returned from method", () => {
    return expect(suggestionsFor("#input.barB.bazC.")).resolves.toMatchSnapshot()
  })

  it("should suggest methods for union objects", () => {
    return expect(suggestionsFor("#union.")).resolves.toMatchSnapshot()
  })

  it("should suggest methods for object returned from method from union objects", () => {
    return expect(suggestionsFor("#union.bazC.")).resolves.toMatchSnapshot()
  })

  it("should suggest in complex expression #1", () => {
    return expect(suggestionsFor("#input.foo + #input.barB.bazC.quax", {row: 0, column: "#input.foo".length })).resolves.toMatchSnapshot()
  })

  it("should suggest in complex expression #2", () => {
    return expect(suggestionsFor("#input.foo + #input.barB.bazC.quax")).resolves.toMatchSnapshot()
  })

  it("should suggest in complex expression #3", () => {
    return expect(suggestionsFor("#input.barB.bazC.quaxString.toUp")).resolves.toMatchSnapshot()
  })


  it("should not suggest anything if suggestion already applied with space at the end", () => {
    return expect(suggestionsFor("#input.fooString ")).resolves.toMatchSnapshot()
  })

  it("should suggest for invocations with method parameters #1", () => {
    return expect(suggestionsFor("#input.foo + #input.barB.bazC('1').quax")).resolves.toMatchSnapshot()
  })

  it("should suggest for invocations with method parameters #2", () => {
    return expect(suggestionsFor("#input.foo + #input.barB.bazC('1', #input.foo, 2).quax")).resolves.toMatchSnapshot()
  })

  it("should suggest for multiline code #1", () => {
    return expect(suggestionsFor("#input\n.fo", {row: 1, column: ".fo".length })).resolves.toMatchSnapshot()
  })

  it("should suggest for multiline code #2", () => {
    return expect(suggestionsFor("#input\n.barB\n.", {row: 2, column: ".".length })).resolves.toMatchSnapshot()
  })

  it("should suggest for multiline code #3", () => {
    return expect(suggestionsFor("#input\n.ba\n.barC", {row: 1, column: ".ba".length })).resolves.toMatchSnapshot()
  })

  it("should omit whitespace formatting in suggest for multiline code #1", () => {
    return expect(suggestionsFor("#input\n  .ba", {row: 1, column: "  .ba".length })).resolves.toMatchSnapshot()
  })

  it("should omit whitespace formatting in suggest for multiline code #2", () => {
    return expect(suggestionsFor("#input\n  .barB\n  .ba", {row: 2, column: "  .ba".length })).resolves.toMatchSnapshot()
  })

  it("should omit whitespace formatting in suggest for multiline code #3", () => {
    return expect(suggestionsFor("#input\n  .ba\n  .bazC", {row: 1, column: "  .ba".length })).resolves.toMatchSnapshot()
  })

  it("should omit whitespace formatting in suggest for multiline code #4", () => {
    return expect(suggestionsFor("#input\n  .barB.ba", {row: 1, column: "  .barB.ba".length })).resolves.toMatchSnapshot()
  })

  it("should omit whitespace formatting in suggest for multiline code #5", () => {
    return expect(suggestionsFor("#input\n  .barB.bazC\n  .quaxString.", {row: 2, column: "  .quaxString.".length })).resolves.toMatchSnapshot()
  })

  it("should suggest field in typed map", () => {
    return expect(suggestionsFor("#dynamicMap.int")).resolves.toMatchSnapshot()
  })

  it("should suggest embedded field in typed map", () => {
    return expect(suggestionsFor("#dynamicMap.aField.f")).resolves.toMatchSnapshot()
  })

  it("should suggest #this fields in simple projection", () => {
    return expect(suggestionsFor("#listVar.listField.![#this.f]", {row: 0, column: "#listVar.listField.![#this.f".length })).resolves.toMatchSnapshot()
  })

  it("should suggest #this fields in projection on union of lists", () => {
    return expect(suggestionsFor("#unionOfLists.![#this.f]", {row: 0, column: "#unionOfLists.![#this.f".length })).resolves.toMatchSnapshot()
  })

  it("should suggest #this fields in projection after selection", () => {
    return expect(suggestionsFor("#listVar.listField.?[#this == 'value'].![#this.f]", {row: 0, column: "#listVar.listField.?[#this == 'value'].![#this.f".length })).resolves.toMatchSnapshot()
  })

  it("handles negated parameters with projections and selections", () => {
    return expect(suggestionsFor("!#listVar.listField.?[#this == 'value'].![#this.f]", {row: 0, column: "!#listVar.listField.?[#this == 'value'].![#this.f".length })).resolves.toMatchSnapshot()
  })

  it("should support nested method invocations", () => {
    return expect(suggestionsFor("#util.now(#other.quaxString.toUpperCase().)", {row: 0, column: "#util.now(#other.quaxString.toUpperCase().".length })).resolves.toMatchSnapshot()
  })

  it("should support safe navigation", () => {
    return expect(suggestionsFor("#input?.barB.bazC?.")).resolves.toMatchSnapshot()
  })
})


describe("remove finished selections from input", () => {

  const expressionSuggester = new RegexExpressionSuggester(typesInformation, variables, null, null)

  it("leaves unfinished selection", () => {
    const original = "#input.one.two.?[#this."
    const removed = expressionSuggester._removeFinishedSelectionFromExpressionPart(original)
    expect(removed).toEqual(original)
  })

  it("leaves projections", () => {
    const original = "#input.one.two.![#this.value]"
    const removed = expressionSuggester._removeFinishedSelectionFromExpressionPart(original)
    expect(removed).toEqual(original)
  })

  it("removes one selection", () => {
    const original = "#input.one.two.?[#this.value == 1].three"
    const removed = expressionSuggester._removeFinishedSelectionFromExpressionPart(original)
    expect(removed).toEqual("#input.one.two.three")
  })

  it("removes many selections", () => {
    const original = "#input.one.two.?[#this.value == 1].three.?[#this.value == 2].four.?[#this.ala =="
    const removed = expressionSuggester._removeFinishedSelectionFromExpressionPart(original)
    expect(removed).toEqual("#input.one.two.three.four.?[#this.ala ==")
  })

})

describe("normalize multiline input", () => {

  const expressionSuggester = new RegexExpressionSuggester(typesInformation, variables, null, null)

  it("normalize multiline input #1", () => {
    const extracted = expressionSuggester._normalizeMultilineInputToSingleLine("#input\n  .barB.bazC\n  .quaxString.", {row: 1, column: "  .barB.bazC".length })
    expect(extracted).toEqual({
      normalizedInput: "#input.barB.bazC.quaxString.",
      normalizedCaretPosition: "#input.barB.bazC".length
    })
  })

  it("normalize multiline input #2", () => {
    const extracted = expressionSuggester._normalizeMultilineInputToSingleLine("#input\n  .barB.bazC\n  .quaxString.", {row: 2, column: "  .quaxString.".length })
    expect(extracted).toEqual({
      normalizedInput: "#input.barB.bazC.quaxString.",
      normalizedCaretPosition: "#input.barB.bazC.quaxString.".length
    })
  })

})
