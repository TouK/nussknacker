import {curry} from "lodash"

export enum QuotationMark {
  single = `'`,
  double = `"`,
}

const defaultQuotationMark = QuotationMark.single

export const escapeQuotes = curry((quotationMark: QuotationMark, value: string): string => {
  return quotationMark === QuotationMark.single ?
    value.replaceAll(quotationMark, `${quotationMark}${quotationMark}`) :
    value
})
export const unescapeQuotes = curry((quotationMark: QuotationMark, string: string): string => {
  return quotationMark === QuotationMark.single ?
    string.replaceAll(`${quotationMark}${quotationMark}`, quotationMark) :
    string
})
export const quote = curry((quotationMark: QuotationMark, value: string): string => {
  return quotationMark + value + quotationMark
})

export const unquote = curry((quotationMark: QuotationMark, quoted: string): string => {
  return quoted.substring(quotationMark.length, quoted.length - quotationMark.length)
})

function startsWithQuotationMark(value: string): boolean {
  return value.startsWith(QuotationMark.double) || value.startsWith(
    QuotationMark.single,
  )
}

export function getQuotationMark(value: string): QuotationMark {
  return startsWithQuotationMark(value) ?
    value.charAt(0) as QuotationMark :
    defaultQuotationMark
}
