import * as TemplatesUtils from "../src/components/graph/node-modal/editors/expression/TemplatesUtils"
import {QuotationMark} from "../src/components/graph/node-modal/editors/expression/SpelQuotesUtils"
import {describe, expect} from "@jest/globals"

const templates = `
Lorem ipsum #{dolor}# sit amet enim. Etiam #{
ullamcorper.
Suspendisse  
  }# a pellentesque dui, non felis. Maecenas malesuada elit lectus
`

const templatesEscaped = `
Lorem ipsum #\\{dolor\\}# sit amet enim. Etiam #\\{
ullamcorper.
Suspendisse  
  \\}# a pellentesque dui, non felis. Maecenas malesuada elit lectus
`

const concats = `'
Lorem ipsum ' + dolor + ' sit amet enim. Etiam '+ullamcorper.
Suspendisse+' a pellentesque dui, non felis. Maecenas malesuada elit lectus
'`

const concatsTrim = `
Lorem ipsum '+dolor+' sit amet enim. Etiam '+ullamcorper.
Suspendisse+' a pellentesque dui, non felis. Maecenas malesuada elit lectus
`

const templatesTrimmed = `
Lorem ipsum #{dolor}# sit amet enim. Etiam #{ullamcorper.
Suspendisse}# a pellentesque dui, non felis. Maecenas malesuada elit lectus
`

describe("TemplatesUtils", () => {
  describe("templatesToConcats", () => {
    it("should replace #{value}# with '+value+'", () => {
      expect(TemplatesUtils.templatesToConcats(QuotationMark.single, templates)).toBe(concatsTrim)
    })
  })
  describe("concatsToTemplates", () => {
    it("should replace '+value+' with #{value}#", () => {
      expect(TemplatesUtils.concatsToTemplates(concats)).toBe(templatesTrimmed)
    })
  })
  describe("escapeTemplates", () => {
    it("should replace #{value}# with #\\{value\\}#", () => {
      expect(TemplatesUtils.escapeTemplates(templates)).toBe(templatesEscaped)
    })
  })
  describe("unescapeTemplates", () => {
    it("should replace #\\{value\\}# with #{value}#", () => {
      expect(TemplatesUtils.unescapeTemplates(templatesEscaped)).toBe(templates)
    })
  })
})
