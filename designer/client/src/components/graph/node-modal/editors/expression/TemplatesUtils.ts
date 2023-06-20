import { curry, flow } from "lodash";
import { getQuotationMark, isQuoted, unescapeQuotes, unquote } from "./SpelQuotesUtils";

// serach for: #{value}# with value in $1
const templatesSearch = /#\{(.*?)\}#/gms;

// serach for: #{value}# with trimmed value in $1
const templatesSearchTrim = /#\{\s*(.+?)\s*\}#/gms;

// serach for: #\{value\}# with value in $1
const escapedTemplatesSearch = /#\\\{(.*?)\\\}#/gms;

export function unescapeTemplates(value: string): string {
    return value.replace(escapedTemplatesSearch, `#{$1}#`);
}

export function escapeTemplates(value: string): string {
    return value.replace(templatesSearch, `#\\{$1\\}#`);
}

export const couplerRegex = /\s*\+\s*/gm;

export const templatesToConcats = curry((quotationMark: string, value: string) =>
    value.replace(templatesSearchTrim, `${quotationMark}+$1+${quotationMark}`),
);

export function splitConcats(value: string) {
    return value.split(/\s*\+\s*/gm);
}

export const concatsToTemplates = (value: string) => {
    return splitConcats(value)
        .map((value) => {
            if (isQuoted(value)) {
                const qm = getQuotationMark(value);
                return flow([unquote(qm), unescapeQuotes(qm), escapeTemplates])(value);
            }
            return `#{${value}}#`;
        })
        .join("");
};
