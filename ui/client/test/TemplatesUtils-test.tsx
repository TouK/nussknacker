import * as TemplatesUtils from "../components/graph/node-modal/editors/expression/TemplatesUtils"
import {QuotationMark} from "../components/graph/node-modal/editors/expression/SpelQuotesUtils"

const templates = `
Lorem ipsum #{dolor} sit amet enim. Etiam #{
ullamcorper.
Suspendisse  
  } a pellentesque dui, non felis. Maecenas malesuada elit lectus
`

const templatesEscaped = `
Lorem ipsum #\\{dolor\\} sit amet enim. Etiam #\\{
ullamcorper.
Suspendisse  
  \\} a pellentesque dui, non felis. Maecenas malesuada elit lectus
`

const concats = `
Lorem ipsum '+dolor+' sit amet enim. Etiam '+ullamcorper.
Suspendisse+' a pellentesque dui, non felis. Maecenas malesuada elit lectus
`

const templatesTrimmed = `
Lorem ipsum #{dolor} sit amet enim. Etiam #{ullamcorper.
Suspendisse} a pellentesque dui, non felis. Maecenas malesuada elit lectus
`

describe("TemplatesUtils", () => {
  describe("templatesToConcats", () => {
    it("should replace #{value} with '+value+' ", () => {
      expect(TemplatesUtils.templatesToConcats(QuotationMark.single, templates)).toBe(concats)
    })
  })
  describe("concatsToTemplates", () => {
    it("should replace #{value} with '+value+' ", () => {
      expect(TemplatesUtils.concatsToTemplates(QuotationMark.single, concats)).toBe(templatesTrimmed)
    })
  })
  describe("escapeTemplates", () => {
    it("should replace #{value} with #\\{value\\}", () => {
      expect(TemplatesUtils.escapeTemplates(templates)).toBe(templatesEscaped)
    })
  })
  describe("unescapeTemplates", () => {
    it("should replace #\\{value\\} with #{value}", () => {
      expect(TemplatesUtils.unescapeTemplates(templatesEscaped)).toBe(templates)
    })
  })
})
