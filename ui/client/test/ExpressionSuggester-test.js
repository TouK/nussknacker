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
  },
  {
    "clazzName": {"refClazzName": "java.lang.String"},
    "methods": {"toUpperCase": {"refClazzName": "java.lang.String"}}
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
    const suggestions = expressionSuggester.suggestionsFor("", {row: 0, column: 0})
    expect(suggestions).toEqual([])
  })

  it("should suggest all global variables if # specified", () => {
    const suggestions = expressionSuggester.suggestionsFor("#", {row: 0, column: "#".length})
    expect(suggestions).toEqual([
      { methodName: "#input", refClazzName: 'org.A'},
      { methodName: "#other", refClazzName: 'org.C'},
      { methodName: "#ANOTHER", refClazzName: 'org.A'}
    ])
  })

  it("should filter global variables suggestions", () => {
    const suggestions = expressionSuggester.suggestionsFor("#ot", {row: 0, column: "#ot".length })
    expect(suggestions).toEqual([{methodName: "#other", refClazzName: 'org.C'}])
  })

  it("should filter uppercase global variables suggestions", () => {
    const suggestions = expressionSuggester.suggestionsFor("#ANO", {row: 0, column: "#ANO".length })
    expect(suggestions).toEqual([{methodName: "#ANOTHER", refClazzName: 'org.A'}])
  })

  it("should suggest global variable", () => {
    const suggestions = expressionSuggester.suggestionsFor("#inpu", {row: 0, column: "#inpu".length })
    expect(suggestions).toEqual([
      { methodName: "#input", refClazzName: 'org.A'}
    ])
  })

  it("should suggest global variable methods", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input.", {row: 0, column: "#input.".length })
    expect(suggestions).toEqual([
      { methodName: 'fooString', refClazzName: 'java.lang.String'},
      { methodName: 'barB', refClazzName: 'org.B' }
    ])
  })

  it("should suggest filtered global variable methods", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input.fo", {row: 0, column: "#input.fo".length })
    expect(suggestions).toEqual([
      { methodName: 'fooString', refClazzName: 'java.lang.String'}
    ])
  })

  it("should suggest filtered global variable methods in newline", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input.fo", {row: 0, column: "#input.fo".length })
    expect(suggestions).toEqual([
      { methodName: 'fooString', refClazzName: 'java.lang.String'}
    ])
  })

  it("should suggest filtered global variable methods based not on beginning of the method", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input.string", {row: 0, column: "#input.string".length })
    expect(suggestions).toEqual([
      { methodName: 'fooString', refClazzName: 'java.lang.String'}
    ])
  })

  it("should suggest methods for object returned from method", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input.barB.bazC.", {row: 0, column: "#input.barB.bazC.".length })
    expect(suggestions).toEqual([
      { methodName: 'quaxString', refClazzName: 'java.lang.String'}
    ])
  })

  it("should suggest in complex expression #1", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input.foo + #input.barB.bazC.quax", {row: 0, column: "#input.foo".length })
    expect(suggestions).toEqual([
      { methodName: 'fooString', refClazzName: 'java.lang.String'}
    ])
  })

  it("should suggest in complex expression #2", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input.foo + #input.barB.bazC.quax", {row: 0, column: "#input.foo + #input.barB.bazC.quax".length })
    expect(suggestions).toEqual([
      { methodName: 'quaxString', refClazzName: 'java.lang.String'}
    ])
  })

  it("should suggest in complex expression #3", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input.barB.bazC.quaxString.toUp", {row: 0, column: "#input.barB.bazC.quaxString.toUp".length })
    expect(suggestions).toEqual([
      { methodName: 'toUpperCase', refClazzName: 'java.lang.String'}
    ])
  })


  it("should not suggest anything if suggestion already applied with space at the end", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input.fooString ", {row: 0, column: "#input.fooString ".length })
    expect(suggestions).toEqual([])
  })

  it("should suggest for invocations with method parameters #1", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input.foo + #input.barB.bazC('1').quax", {row: 0, column: "#input.foo + #input.barB.bazC('1').quax".length })
    expect(suggestions).toEqual([
      { methodName: 'quaxString', refClazzName: 'java.lang.String'}
    ])
  })

  it("should suggest for invocations with method parameters #2", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input.foo + #input.barB.bazC('1', #input.foo, 2).quax", {row: 0, column: "#input.foo + #input.barB.bazC('1', #input.foo, 2).quax".length })
    expect(suggestions).toEqual([
      { methodName: 'quaxString', refClazzName: 'java.lang.String'}
    ])
  })

  it("should suggest for multiline code #1", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input\n.fo", {row: 1, column: ".fo".length })
    expect(suggestions).toEqual([
      { methodName: 'fooString', refClazzName: 'java.lang.String'}
    ])
  })

  it("should suggest for multiline code #2", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input\n.barB\n.", {row: 2, column: ".".length })
    expect(suggestions).toEqual([
      { methodName: 'bazC', refClazzName: 'org.C'}
    ])
  })

  it("should suggest for multiline code #3", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input\n.ba\n.barC", {row: 1, column: ".ba".length })
    expect(suggestions).toEqual([
      { methodName: 'barB', refClazzName: 'org.B'}
    ])
  })

  it("should omit whitespace formatting in suggest for multiline code #1", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input\n  .ba", {row: 1, column: "  .ba".length })
    expect(suggestions).toEqual([
      { methodName: 'barB', refClazzName: 'org.B'}
    ])
  })

  it("should omit whitespace formatting in suggest for multiline code #2", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input\n  .barB\n  .ba", {row: 2, column: "  .ba".length })
    expect(suggestions).toEqual([
      { methodName: 'bazC', refClazzName: 'org.C'}
    ])
  })

  it("should omit whitespace formatting in suggest for multiline code #3", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input\n  .ba\n  .bazC", {row: 1, column: "  .ba".length })
    expect(suggestions).toEqual([
      { methodName: 'barB', refClazzName: 'org.B'}
    ])
  })

  it("should omit whitespace formatting in suggest for multiline code #4", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input\n  .barB.ba", {row: 1, column: "  .barB.ba".length })
    expect(suggestions).toEqual([
      { methodName: 'bazC', refClazzName: 'org.C'}
    ])
  })

  it("should omit whitespace formatting in suggest for multiline code #5", () => {
    const suggestions = expressionSuggester.suggestionsFor("#input\n  .barB.bazC\n  .quaxString.", {row: 2, column: "  .quaxString.".length })
    expect(suggestions).toEqual([
      { methodName: 'toUpperCase', refClazzName: 'java.lang.String'}
    ])
  })


})

describe("normalize multiline input", () => {
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