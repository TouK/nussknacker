import ExpressionSuggester from '../components/graph/node-modal/editors/expression/ExpressionSuggester'
import {map} from "lodash"

const typesInformation = [
  {
    "clazzName": {"refClazzName": "org.A"},
    "methods": {"fooString": {"refClazz": {"refClazzName": "java.lang.String"} }, "barB": {"refClazz": {"refClazzName": "org.B"} } }
  },
  {
    "clazzName": {"refClazzName": "org.B"},
    "methods": {"bazC": {"refClazz": {"refClazzName": "org.C"} } }
  },
  {
    "clazzName": {"refClazzName": "org.C"},
    "methods": {"quaxString": {"refClazz": {"refClazzName": "java.lang.String"} }}
  },
  {
    "clazzName": {"refClazzName": "org.AA"},
    "methods": {"fooString": {"refClazz": {"refClazzName": "java.lang.String"} }, "barB": {"refClazz": {"refClazzName": "org.C"} } }
  },
  {
    "clazzName": {"refClazzName": "org.WithList"},
    "methods": {"listField": {"refClazz": {"refClazzName": "java.util.List", params: [{refClazzName: "org.A"}]} }}
  },
  {
    "clazzName": {"refClazzName": "java.lang.String"},
    "methods": {"toUpperCase": {"refClazz": {"refClazzName": "java.lang.String"} }}
  },
  {
    "clazzName": {"refClazzName": "java.util.LocalDateTime"},
    "methods": {"isBefore": {"refClazz": {"refClazzName": "java.lang.Boolean"}, "params": {"name": "arg0", "refClazz": "java.util.LocalDateTime"}}}
  },
  {
    "clazzName": {"refClazzName": "org.Util"},
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

const suggestionsFor = (inputValue, caretPosition2d, stubbedDictSuggestions) => {
  const _caretPosition2d = caretPosition2d ? caretPosition2d : { row: 0, column: inputValue.length }
  const stubService = {
    fetchDictLabelSuggestions(processingType, dictId, labelPattern) {
      return new Promise(resolve => resolve({ data: stubbedDictSuggestions }))
    }
  }
  const expressionSuggester = new ExpressionSuggester(typesInformation, variables, "fooProcessingType", stubService)
  return expressionSuggester.suggestionsFor(inputValue, _caretPosition2d)
}

describe("expression suggester", () => {
  it("should not suggest anything for empty input", () => {
    suggestionsFor("").then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    })
  })

  it("should suggest all global variables if # specified", (done) => {
    suggestionsFor("#").then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should filter global variables suggestions", (done) => {
    suggestionsFor("#ot").then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should filter uppercase global variables suggestions", (done) => {
    suggestionsFor("#ANO").then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should suggest global variable", (done) => {
    suggestionsFor("#inpu").then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should suggest global variable methods", (done) => {
    suggestionsFor("#input.").then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should suggest dict variable methods", (done) => {
    let stubbedDictSuggestions = [
      { key: "one", label: "One" },
      { key: "two", label: "Two" }
    ]
    suggestionsFor("#dict.", null, stubbedDictSuggestions).then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })


  it("should suggest dict variable methods using indexer syntax", () => {
    let stubbedDictSuggestions = [
      { key: "sentence-with-spaces-and-dots", label: "Sentence with spaces and . dots" }
    ]
    const correctInputs = ["#dict['", "#dict['S", "#dict['Sentence w", "#dict['Sentence with spaces and . dots"]
    map(correctInputs, inputValue => {
      suggestionsFor(inputValue, null, stubbedDictSuggestions).then(suggestions => {
        expect(suggestions).toMatchSnapshot()
      })
    })
  })

  it("should suggest filtered global variable methods", (done) => {
    suggestionsFor("#input.fo").then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should suggest filtered global variable methods based not on beginning of the method", (done) => {
    suggestionsFor("#input.string").then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should suggest methods for object returned from method", (done) => {
    suggestionsFor("#input.barB.bazC.").then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should suggest methods for union objects", (done) => {
    suggestionsFor("#union.").then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should suggest methods for object returned from method from union objects", (done) => {
    suggestionsFor("#union.bazC.").then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should suggest in complex expression #1", (done) => {
    suggestionsFor("#input.foo + #input.barB.bazC.quax", {row: 0, column: "#input.foo".length }).then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should suggest in complex expression #2", (done) => {
    suggestionsFor("#input.foo + #input.barB.bazC.quax").then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should suggest in complex expression #3", (done) => {
    suggestionsFor("#input.barB.bazC.quaxString.toUp").then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })


  it("should not suggest anything if suggestion already applied with space at the end", (done) => {
    suggestionsFor("#input.fooString ").then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should suggest for invocations with method parameters #1", (done) => {
    suggestionsFor("#input.foo + #input.barB.bazC('1').quax").then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should suggest for invocations with method parameters #2", (done) => {
    suggestionsFor("#input.foo + #input.barB.bazC('1', #input.foo, 2).quax").then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should suggest for multiline code #1", (done) => {
    suggestionsFor("#input\n.fo", {row: 1, column: ".fo".length }).then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should suggest for multiline code #2", (done) => {
    suggestionsFor("#input\n.barB\n.", {row: 2, column: ".".length }).then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should suggest for multiline code #3", (done) => {
    suggestionsFor("#input\n.ba\n.barC", {row: 1, column: ".ba".length }).then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should omit whitespace formatting in suggest for multiline code #1", (done) => {
    suggestionsFor("#input\n  .ba", {row: 1, column: "  .ba".length }).then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should omit whitespace formatting in suggest for multiline code #2", (done) => {
    suggestionsFor("#input\n  .barB\n  .ba", {row: 2, column: "  .ba".length }).then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should omit whitespace formatting in suggest for multiline code #3", (done) => {
    suggestionsFor("#input\n  .ba\n  .bazC", {row: 1, column: "  .ba".length }).then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should omit whitespace formatting in suggest for multiline code #4", (done) => {
    suggestionsFor("#input\n  .barB.ba", {row: 1, column: "  .barB.ba".length }).then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should omit whitespace formatting in suggest for multiline code #5", (done) => {
    suggestionsFor("#input\n  .barB.bazC\n  .quaxString.", {row: 2, column: "  .quaxString.".length }).then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should suggest field in typed map", (done) => {
    suggestionsFor("#dynamicMap.int").then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should suggest embedded field in typed map", (done) => {
    suggestionsFor("#dynamicMap.aField.f").then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should suggest #this fields in simple projection", (done) => {
    suggestionsFor("#listVar.listField.![#this.f]", {row: 0, column: "#listVar.listField.![#this.f".length }).then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should suggest #this fields in projection on union of lists", (done) => {
    suggestionsFor("#unionOfLists.![#this.f]", {row: 0, column: "#unionOfLists.![#this.f".length }).then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should suggest #this fields in projection after selection", (done) => {
    suggestionsFor("#listVar.listField.?[#this == 'value'].![#this.f]", {row: 0, column: "#listVar.listField.?[#this == 'value'].![#this.f".length }).then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("handles negated parameters with projections and selections", (done) => {
    suggestionsFor("!#listVar.listField.?[#this == 'value'].![#this.f]", {row: 0, column: "!#listVar.listField.?[#this == 'value'].![#this.f".length }).then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should support nested method invocations", (done) => {
    suggestionsFor("#util.now(#other.quaxString.toUpperCase().)", {row: 0, column: "#util.now(#other.quaxString.toUpperCase().".length }).then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })

  it("should support safe navigation", (done) => {
    suggestionsFor("#input?.barB.bazC?.").then(suggestions => {
      expect(suggestions).toMatchSnapshot()
    }).then(done)
  })
})


describe("remove finished selections from input", () => {

  const expressionSuggester = new ExpressionSuggester(typesInformation, variables)

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

  const expressionSuggester = new ExpressionSuggester(typesInformation, variables)

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
