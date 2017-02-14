import ExpressionSuggester from '../components/graph/ExpressionSuggester'

const typesInformation = [
  {
    "clazzName": {"refClazzName": "org.A"},
    "methods": {"fooString": {"refClazzName": "java.lang.String"}, "barB": {"refClazzName": "org.B"}}
  },
  {
    "clazzName": {"refClazzName": "org.B"},
    "methods": {"bazC": {"refClazzName": "org.C"}}
  },
  {
    "clazzName": {"refClazzName": "org.C"},
    "methods": {"quaxString": {"refClazzName": "java.lang.String"}}
  }
]

const variables = {
  "#input": "org.A",
  "#other": "org.C",
  "#ANOTHER": "org.A"
}

const expressionSuggester = new ExpressionSuggester(typesInformation, variables)

describe("expression suggester", () => {
  it("should not suggest anything for empty input", () => {
    const suggestions = expressionSuggester.suggestionsFor("", 0)
    expect(suggestions).toEqual([])
  })

  it("should suggest all global variables if # specified", () => {
    const suggestions = expressionSuggester.suggestionsFor("#", "#".length)
    expect(suggestions).toEqual([
      { methodName: "#input"},
      { methodName: "#other"},
      { methodName: "#ANOTHER"}
    ])
  })

  it("should filter global variables suggestions", () => {
    const suggestions = expressionSuggester.suggestionsFor("#ot", "#ot".length)
    expect(suggestions).toEqual([{methodName: "#other"}])
  })

  it("should filter uppercase global variables suggestions", () => {
    const suggestions = expressionSuggester.suggestionsFor("#ANO", "#ANO".length)
    expect(suggestions).toEqual([{methodName: "#ANOTHER"}])
  })

  it("should suggest global variable", () => {
    const suggestions = expressionSuggester.suggestionsFor("#inpu", "#inpu".length)
    expect(suggestions).toEqual([
      { methodName: "#input"}
    ])
  })

  it("should suggest global variable methods", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input.", "#input.".length)
    expect(suggestions).toEqual([
      { methodName: 'fooString', refClazzName: 'java.lang.String'},
      { methodName: 'barB', refClazzName: 'org.B' }
    ])
  })

  it("should suggest filtered global variable methods", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input.fo", "#input.fo".length)
    expect(suggestions).toEqual([
      { methodName: 'fooString', refClazzName: 'java.lang.String'}
    ])
  })

  it("should suggest filtered global variable methods based not on beginning of the method", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input.string", "#input.string".length)
    expect(suggestions).toEqual([
      { methodName: 'fooString', refClazzName: 'java.lang.String'}
    ])
  })

  it("should suggest methods for object returned from method", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input.barB.bazC.", "#input.barB.bazC.".length)
    expect(suggestions).toEqual([
      { methodName: 'quaxString', refClazzName: 'java.lang.String'}
    ])
  })

  it("should suggest in complex expression #1", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input.foo + #input.barB.bazC.quax", "#input.foo".length)
    expect(suggestions).toEqual([
      { methodName: 'fooString', refClazzName: 'java.lang.String'}
    ])
  })

  it("should suggest in complex expression #2", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input.foo + #input.barB.bazC.quax", "#input.foo + #input.barB.bazC.quax".length)
    expect(suggestions).toEqual([
      { methodName: 'quaxString', refClazzName: 'java.lang.String'}
    ])
  })

  it("should not suggest anything if suggestion already applied with space at the end", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input.fooString ", "#input.fooString ".length)
    expect(suggestions).toEqual([])
  })

})

describe("extract matching part from input", () => {
  it("should extract matching suggestion part from method beginning", () => {
    const fooMethod = { methodName: 'fooString', refClazzName: 'java.lang.String'};
    const extracted = expressionSuggester.extractMatchingPartFromInput(fooMethod, "#input.foo", "#input.foo".length)
    expect(extracted).toEqual({
      start: "",
      middle: "foo",
      end: "String"
    })
  })

  it("should extract matching suggestion part from method middle", () => {
    const fooMethod = { methodName: 'fooString', refClazzName: 'java.lang.String'};
    const extracted = expressionSuggester.extractMatchingPartFromInput(fooMethod, "#input.oStr", "#input.oStr".length)
    expect(extracted).toEqual({
      start: "fo",
      middle: "oStr",
      end: "ing"
    })
  })

  it("should extract matching suggestion part from global variable beginning", () => {
    const fooMethod = { methodName: '#input'};
    const extracted = expressionSuggester.extractMatchingPartFromInput(fooMethod, "#inp", "#inp".length)
    expect(extracted).toEqual({
      start: "",
      middle: "#inp",
      end: "ut"
    })
  })

  it("should make suggestion type readable", () => {
    expect(expressionSuggester.humanReadableSuggestionType({refClazzName: "java.lang.String", methodName: "a"})).toEqual("String")
    expect(expressionSuggester.humanReadableSuggestionType({refClazzName: "int", methodName: "a"})).toEqual("Int")
  })
})


describe("suggestion applied", () => {
  it("should apply suggestion for simple case", () => {
    const fooMethod = { methodName: 'fooString', refClazzName: 'java.lang.String'};
    const suggestionApplied = expressionSuggester.applySuggestion(fooMethod, "#input.foo", "#input.foo".length)
    expect(suggestionApplied).toEqual({
      value: "#input.fooString",
      caretPosition: "#input.fooString".length
    })
  })

  it("should apply suggestion for more complex case", () => {
    const fooMethod = { methodName: 'fooString', refClazzName: 'java.lang.String'};
    const extracted = expressionSuggester.applySuggestion(fooMethod, "#input.foo + #input.barB.bazC.quax", "#input.foo".length)
    expect(extracted).toEqual({
      value: "#input.fooString + #input.barB.bazC.quax",
      caretPosition: "#input.fooString".length
    })
  })

})

