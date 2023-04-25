import {curry, flow} from "lodash"

export enum QuotationMark {
  single = `'`,
  double = `"`,
}

const defaultQuotationMark = QuotationMark.single

const replace = (value: string, searchValue: string, replaceValue: string) => value.replaceAll ?
  value.replaceAll(searchValue, replaceValue) :
  value.split(searchValue).join(replaceValue)

export const escapeQuotes = curry((quotationMark: QuotationMark | string, value: string): string => {
  switch (quotationMark) {
    case QuotationMark.single:
    case QuotationMark.double:
      return replace(value, quotationMark, `${quotationMark}${quotationMark}`)
    default:
      return value
  }
})

export const unescapeQuotes = curry((quotationMark: QuotationMark | string, value: string): string => {
  switch (quotationMark) {
    case QuotationMark.single:
    case QuotationMark.double:
      return replace(value, `${quotationMark}${quotationMark}`, quotationMark)
    default:
      return value
  }
})

export const quote = curry((quotationMark: QuotationMark, value: string): string => {
  if (Object.values(QuotationMark).includes(quotationMark)) {
    return quotationMark + value + quotationMark
  }
  return value
})

const switchQuotes = curry((quoteBefore: QuotationMark, quoteAfter: QuotationMark, value: string): string => {
  return flow([
    unquote(quoteBefore),
    unescapeQuotes(quoteBefore),
    escapeQuotes(quoteAfter),
    quote(quoteAfter),
  ])(value)
})

export const simplifyQuotes = curry((qm: QuotationMark, value: string): string => {
  if (value.startsWith(`${qm}${qm}${qm}`)) {
    const nextQm = qm === QuotationMark.single ? QuotationMark.double : QuotationMark.single
    return switchQuotes(qm, nextQm, value)
  }
  return value
})

export const unquote = curry((quotationMark: QuotationMark, quoted: string): string => {
  if (Object.values(QuotationMark).includes(quotationMark)) {
    return quoted.replace(RegExp(`^${quotationMark}|${quotationMark}$`, `g`), ``)
  }
  return quoted
})

const escapedOnly = mark => `([^(${mark})]|(${escapeQuotes(mark, mark)}))*`

const getQuotedStringPattern = (marks: string[]): RegExp => {
  const patterns = marks.map(mark => `${mark}${escapedOnly(mark)}${mark}`)
  return RegExp(`^\\s*(${patterns.join("|")})\\s*$`)
}

const quotedStringPattern = getQuotedStringPattern([QuotationMark.single, QuotationMark.double])

export const isQuoted = (value: string): boolean => quotedStringPattern.test(value)

export function getQuotationMark(value: string): QuotationMark {
  switch (value.charAt(0)) {
    case QuotationMark.single:
      if (value.endsWith(QuotationMark.single)) {
        return QuotationMark.single
      }
      return QuotationMark.double
    case QuotationMark.double:
      if (value.endsWith(QuotationMark.double)) {
        return QuotationMark.double
      }
      return QuotationMark.single
    default:
      return defaultQuotationMark
  }
}
