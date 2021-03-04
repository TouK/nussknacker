import {curry} from "lodash"

const templatesSearch = /#\{\s*(.+?)\s*\}/gms
const escapedTemplatesSearch = /#\\\{\s*(.+?)\s*\\\}/gms

export function unescapeTemplates(value: string): string {
  return value.replace(escapedTemplatesSearch, `#{$1}`)
}

export function escapeTemplates(value: string): string {
  return value.replace(templatesSearch, `#\\{$1\\}`)
}

function getConcatsSearch(quotationMark: string): RegExp {
  return RegExp(`${quotationMark}\\+\s*(.+?)\s*\\+${quotationMark}`, `gms`)
}

export const templatesToConcats = curry((quotationMark: string, value: string) => value.replace(
  templatesSearch,
  `${quotationMark}+$1+${quotationMark}`,
))

export const concatsToTemplates = curry((quotationMark: string, value: string) => value.replace(
  getConcatsSearch(quotationMark),
  `#{$1}`,
))
