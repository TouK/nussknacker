import {curry} from "lodash"
import {dotAllReplacement} from "../../../../../common/regexpCompat"

export enum QuotationMark {
  single = `'`,
  double = `"`,
}

const defaultQuotationMark = QuotationMark.single

export const escapeQuotes = curry((quotationMark: QuotationMark, value: string): string => {
  return quotationMark === QuotationMark.single ?
    value.replaceAll ?
      value.replaceAll(quotationMark, `${quotationMark}${quotationMark}`) :
      value.split(quotationMark).join(`${quotationMark}${quotationMark}`) :
    value
})

export const unescapeQuotes = curry((quotationMark: QuotationMark, value: string): string => {
  return quotationMark === QuotationMark.single ?
    value.replaceAll ?
      value.replaceAll(`${quotationMark}${quotationMark}`, quotationMark) :
      value.split(`${quotationMark}${quotationMark}`).join(quotationMark) :
    value
})

export const quote = curry((quotationMark: QuotationMark, value: string): string => {
  if (Object.values(QuotationMark).includes(quotationMark)) {
    return quotationMark + value + quotationMark
  }
  return value
})

export const unquote = curry((quotationMark: QuotationMark, quoted: string): string => {
  if (Object.values(QuotationMark).includes(quotationMark)) {
    return quoted.replace(RegExp(`^${quotationMark}|${quotationMark}$`, `g`), ``)
  }
  return quoted
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

export function getQuotedStringPattern(quotationMarks: string[]): RegExp {
  const patterns = quotationMarks.map(mark => `^${mark}${dotAllReplacement}*${mark}$`)
  return RegExp(patterns.join(`|`))
}
