import React from "react"
import {Formatter, FormatterType, spelFormatters} from "../components/graph/node-modal/editors/expression/Formatter"
import {describe, expect} from '@jest/globals';

const text = `
Lorem ipsum #{dolor}# sit amet #\\{enim\\}#. Etiam #{
ullamcorper.
Suspendisse  
  }# a pellentesque dui, non #{ felis }#. Maecenas malesuada elit lectus
`

const textEncoded = `'
Lorem ipsum '+dolor+' sit amet #{enim}#. Etiam '+ullamcorper.
Suspendisse+' a pellentesque dui, non '+felis+'. Maecenas malesuada elit lectus
'`

const textDecoded = `
Lorem ipsum #{dolor}# sit amet #\\{enim\\}#. Etiam #{ullamcorper.
Suspendisse}# a pellentesque dui, non #{felis}#. Maecenas malesuada elit lectus
`

describe("Formatter", () => {
  let spelFormatter: Formatter
  describe("spelFormatters", () => {
    describe("for SQL", () => {
      beforeEach(() => {
        spelFormatter = spelFormatters[FormatterType.Sql]
      })

      it("should be same as for String", () => {
        expect(spelFormatter).toBe(spelFormatters[FormatterType.String])
      })

      it("should encode", () => {
        expect(spelFormatter.encode(text)).toBe(textEncoded)
      })
      it("should decode", () => {
        expect(spelFormatter.decode(textEncoded)).toBe(textDecoded)
      })
    })
  })
})
