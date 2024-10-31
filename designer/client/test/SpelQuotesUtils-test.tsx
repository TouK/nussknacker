import * as SpelQuotesUtils from "../src/components/graph/node-modal/editors/expression/SpelQuotesUtils";
import { isQuoted, QuotationMark } from "../src/components/graph/node-modal/editors/expression/SpelQuotesUtils";
import { jest } from "@jest/globals";
import { stringSpelFormatter } from "../src/components/graph/node-modal/editors/expression/Formatter";

const text = `a'b'c"d"e'f'g`;

describe("SpelQuotesUtils", () => {
    describe("escapeQuotes", () => {
        it("should return escaped text", () => {
            expect(SpelQuotesUtils.escapeQuotes(QuotationMark.single, text)).toBe(`a''b''c"d"e''f''g`);
            expect(SpelQuotesUtils.escapeQuotes(QuotationMark.double, text)).toBe(`a'b'c""d""e'f'g`);
            expect(SpelQuotesUtils.escapeQuotes("{unknown}", text)).toBe(text);
        });

        it("should use replaceAll when available", () => {
            const spy = jest.spyOn(String.prototype, "replaceAll");
            SpelQuotesUtils.escapeQuotes(QuotationMark.single, text);
            expect(spy).toBeCalledWith(`'`, `''`);
        });
    });

    describe("unescapeQuotes", () => {
        it("should return unescaped text", () => {
            expect(SpelQuotesUtils.unescapeQuotes(QuotationMark.single, `a''b''c"d"e''f''g`)).toBe(text);
            expect(SpelQuotesUtils.unescapeQuotes(QuotationMark.double, `a'b'c""d""e'f'g`)).toBe(text);
            expect(SpelQuotesUtils.unescapeQuotes("{unknown}", `a'b'c""d""e''f''g`)).toBe(`a'b'c""d""e''f''g`);
        });

        it("should ignore QuotationMark.double marks", () => {
            const text = `a''b''c"d"e''f''g`;
            expect(SpelQuotesUtils.unescapeQuotes(QuotationMark.double, text)).toBe(text);
        });

        it("should use replaceAll when available", () => {
            const spy = jest.spyOn(String.prototype, "replaceAll");
            SpelQuotesUtils.unescapeQuotes(QuotationMark.single, text);
            expect(spy).toBeCalledWith(`''`, `'`);
        });
    });

    describe("quote", () => {
        it("should return wrapped text", () => {
            expect(SpelQuotesUtils.quote(QuotationMark.single, `unquoted`)).toBe(`'unquoted'`);
            expect(SpelQuotesUtils.quote(QuotationMark.double, `unquoted`)).toBe(`"unquoted"`);
        });

        it("should ignore unknown marks", () => {
            // @ts-ignore
            expect(SpelQuotesUtils.quote(`{mark}`, `unquoted`)).toBe(`unquoted`);
        });
    });

    describe("unquote", () => {
        it("should return unwrapped text", () => {
            expect(SpelQuotesUtils.unquote(QuotationMark.single, `'quoted'`)).toBe(`quoted`);
            expect(SpelQuotesUtils.unquote(QuotationMark.double, `"quoted"`)).toBe(`quoted`);
        });

        it("should ignore wrong quotes", () => {
            expect(SpelQuotesUtils.unquote(QuotationMark.single, `"quoted"`)).toBe(`"quoted"`);
        });

        it("should ignore unknown marks", () => {
            // @ts-ignore
            expect(SpelQuotesUtils.unquote(`{mark}`, `{mark}quoted{mark}`)).toBe(`{mark}quoted{mark}`);
        });
    });

    describe("isQuoted", () => {
        const notMatching = [
            `    ## ## ##    `, // not quoted
            `   '## ## ##"   `, // mixed quotes
            `   "## ## ##'   `,
            `   '##'##'##'   `, // unescaped
            `  ''## ## ##''  `,
            ` '''##'##'##''' `,
            `  ""##'##'##""  `,
            `   "##"##"##"   `,
            ` """##"##"##""" `,
        ];

        const matching = [
            `  '##''##''##'  `,
            `'''##''##''##'''`,
            ` '''##"##"##''' `,
            `   "##'##'##"   `,
            `  "##""##""##"  `,
            ` """##'##'##""" `,
            `"""##""##""##"""`,
        ];

        notMatching.forEach((text) => {
            it(`should not match ${text.trim()}`, () => {
                expect(isQuoted(text)).toBeFalsy();
            });
        });

        matching.forEach((text) => {
            it(`should match ${text.trim()}`, () => {
                expect(isQuoted(text)).toBeTruthy();
            });
        });
    });

    describe("getQuotationMark", () => {
        it("should return current quotation mark", () => {
            expect(SpelQuotesUtils.getQuotationMark(`'single'`)).toBe(`'`);
            expect(SpelQuotesUtils.getQuotationMark(`"double"`)).toBe(`"`);
        });

        it("should return default quotation mark when none", () => {
            expect(SpelQuotesUtils.getQuotationMark(`none`)).toBe(`'`);
        });

        it("should return default quotation mark when wrong used", () => {
            expect(SpelQuotesUtils.getQuotationMark("`wrong`")).toBe(`'`);
        });
    });
});

describe("stringSpelFormatter", () => {
    const values = [
        //plain         | expected string  | equal strings...
        [`###`, `'###'`, `"###"`],
        [`"###"`, `""###""`, `""###""`],
        [`'###'`, `''###''`, `''###''`],
        [`'###"`, `''###"'`, `''###"'`],
        [`"###`, `""###"`, `""###"`],
        [`'###`, `''###'`, `''###'`],
        [`###'###`, `'###'###'`, `"###'###"`],
        [`###"###`, `'###"###'`, `"###"###"`],
        [`#{123}#aaa`, `'#{123}#aaa'`],
        [`aaa#{123}#bbb`, `'aaa#{123}#bbb'`, `'aaa#{123}#bbb'`, `"aaa#{123}#bbb"`, `"aaa#{123}#bbb'`, `'aaa#{123}#bbb"`],
    ];

    values.forEach(([value]) => {
        it(`should decode encoded value ${value}`, () => {
            expect(stringSpelFormatter.decode(stringSpelFormatter.encode(value))).toBe(value);
        });
    });

    describe("encoder", () => {
        values.forEach(([value, expected]) => {
            it(`should encode value ${value}`, () => {
                expect(stringSpelFormatter.encode(value)).toBe(expected);
            });
        });
    });

    describe("decoder", () => {
        values.forEach(([expected, ...values]) => {
            values.forEach((value) => {
                it(`should decode value ${value}`, () => {
                    expect(stringSpelFormatter.decode(value)).toBe(expected);
                });
            });
        });
    });
});
