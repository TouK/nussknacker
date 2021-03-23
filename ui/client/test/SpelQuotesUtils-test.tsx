import * as SpelQuotesUtils from "../components/graph/node-modal/editors/expression/SpelQuotesUtils"
import {QuotationMark} from "../components/graph/node-modal/editors/expression/SpelQuotesUtils"

const text = `a'b'c"d"e'f'g`

describe("SpelQuotesUtils", () => {
  describe("escapeQuotes", () => {
    it("should return escaped text", () => {
      expect(SpelQuotesUtils.escapeQuotes(QuotationMark.single, text))
        .toBe(`a''b''c"d"e''f''g`)
    })

    it("should ignore QuotationMark.double marks", () => {
      expect(SpelQuotesUtils.escapeQuotes(QuotationMark.double, text)).toBe(text)
    })

    it("should use replaceAll when available", () => {
      String.prototype.replaceAll = jest.fn()
      SpelQuotesUtils.escapeQuotes(QuotationMark.single, text)
      expect(String.prototype.replaceAll).toBeCalledWith(`'`, `''`)
      String.prototype.replaceAll = undefined
    })
  })

  describe("unescapeQuotes", () => {
    it("should return unescaped text", () => {
      expect(SpelQuotesUtils.unescapeQuotes(QuotationMark.single, `a''b''c"d"e''f''g`)).toBe(text)
    })

    it("should ignore QuotationMark.double marks", () => {
      const text = `a''b''c"d"e''f''g`
      expect(SpelQuotesUtils.unescapeQuotes(QuotationMark.double, text)).toBe(text)
    })

    it("should use replaceAll when available", () => {
      String.prototype.replaceAll = jest.fn()
      SpelQuotesUtils.unescapeQuotes(QuotationMark.single, text)
      expect(String.prototype.replaceAll).toBeCalledWith(`''`, `'`)
      String.prototype.replaceAll = undefined
    })
  })

  describe("quote", () => {
    it("should return wrapped text", () => {
      expect(SpelQuotesUtils.quote(QuotationMark.single, `unquoted`)).toBe(`'unquoted'`)
      expect(SpelQuotesUtils.quote(QuotationMark.double, `unquoted`)).toBe(`"unquoted"`)
    })

    it("should ignore unknown marks", () => {
      // @ts-ignore
      expect(SpelQuotesUtils.quote(`{mark}`, `unquoted`)).toBe(`unquoted`)
    })
  })

  describe("unquote", () => {
    it("should return unwrapped text", () => {
      expect(SpelQuotesUtils.unquote(QuotationMark.single, `'quoted'`)).toBe(`quoted`)
      expect(SpelQuotesUtils.unquote(QuotationMark.double, `"quoted"`)).toBe(`quoted`)
    })

    it("should ignore wrong quotes", () => {
      expect(SpelQuotesUtils.unquote(QuotationMark.single, `"quoted"`)).toBe(`"quoted"`)
    })

    it("should ignore unknown marks", () => {
      // @ts-ignore
      expect(SpelQuotesUtils.unquote(`{mark}`, `{mark}quoted{mark}`)).toBe(`{mark}quoted{mark}`)
    })
  })

  describe("getQuotedStringPattern", () => {
    it("should return right pattern", () => {
      expect(SpelQuotesUtils.getQuotedStringPattern([`'`, `"`]).toString()).toBe(`/^'[\\0-\uFFFF]*'$|^\"[\\0-\uFFFF]*\"$/`)
    })
  })

  describe("getQuotationMark", () => {
    it("should return current quotation mark", () => {
      expect(SpelQuotesUtils.getQuotationMark(`'single'`)).toBe(`'`)
      expect(SpelQuotesUtils.getQuotationMark(`"double"`)).toBe(`"`)
    })

    it("should return default quotation mark when none", () => {
      expect(SpelQuotesUtils.getQuotationMark(`none`)).toBe(`'`)
    })

    it("should return default quotation mark when wrong used", () => {
      expect(SpelQuotesUtils.getQuotationMark("`wrong`")).toBe(`'`)
    })
  })
})
