import {curry} from "lodash"

// serach for: #{value} with value in $1
const templatesSearch = /#\{(.*?)\}/gms

// serach for: #{value} with trimmed value in $1
const templatesSearchTrim = /#\{\s*(.+?)\s*\}/gms

// serach for: #\{value\} with value in $1
const escapedTemplatesSearch = /#\\\{(.*?)\\\}/gms

export function unescapeTemplates(value: string): string {
  return value.replace(escapedTemplatesSearch, `#{$1}`)
}

export function escapeTemplates(value: string): string {
  return value.replace(templatesSearch, `#\\{$1\\}`)
}

// serach for: "quotationMark+value+quotationMark" with trimmed value in $1
function getConcatsSearch(quotationMark: string): RegExp {
  return RegExp(`${quotationMark}\\+\\s*(.+?)\\s*\\+${quotationMark}`, `gms`)
}

export const templatesToConcats = curry((quotationMark: string, value: string) => value.replace(
  templatesSearchTrim,
  `${quotationMark}+$1+${quotationMark}`,
))

export const concatsToTemplates = curry((quotationMark: string, value: string) => value.replace(
  getConcatsSearch(quotationMark),
  `#{$1}`,
))
