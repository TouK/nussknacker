import { getAst, parseToList, parseToObject, printFragment } from "./pareserHelpers";

describe("parserHelpers", () => {
    describe("getAst", () => {
        it("should parse valid SpEL expression", () => {
            const ast = getAst("{1, 2, 3}");
            expect(ast).not.toBeNull();
            expect(ast?.type).toBe("InlineList");
        });

        it("should return null for invalid expression", () => {
            const ast = getAst("((((");
            expect(ast).toBeNull();
        });

        it("should handle graceful mode", () => {
            const ast = getAst("#x.", true);
            expect(ast).not.toBeNull();
        });
    });

    describe("printFragment", () => {
        it("should extract substring by range", () => {
            const text = "hello world";
            const result = printFragment(text, { start: 0, end: 5 });
            expect(result).toBe("hello");
        });

        it("should trim whitespace", () => {
            const text = "  hello  ";
            const result = printFragment(text, { start: 0, end: 9 });
            expect(result).toBe("hello");
        });

        it("should return empty string for empty text", () => {
            const result = printFragment("", { start: 0, end: 0 });
            expect(result).toBe("");
        });
    });

    describe("parseToList", () => {
        it.each([
            [`aaa`, null],
            [`"aaa"`, null],
            [`{`, null],
            [`{}.xxxx`, null],
            [`{}`, []],
            [`#COLLECTION.join({}, "|")`, null],
            [`#xxxx.yyyy({}, "|")`, null],
            [`{123}`, ["123"]],
            [`{ 123 }`, ["123"]],
            [`{123,456}`, ["123", "456"]],
            [`{ 123, 456 }`, ["123", "456"]],
            [`{ 123, aaa, 456 }`, ["123", "aaa", "456"]],
            [`{ 123, aaa.bbb }`, ["123", "aaa.bbb"]],
            [`{ 123, aaa_bbb }`, ["123", "aaa_bbb"]],
            [`{ 123, aaa bbb, ccc }`, ["123", "aaa", "bbb", "ccc"]],
            [`{ 123, "aaa bbb" }`, ["123", `"aaa bbb"`]],
            [`{ 123, 'aaa bbb' }`, ["123", `'aaa bbb'`]],
            [`{ 123, #aaa.bbb }`, ["123", `#aaa.bbb`]],
            [`{ 123, 123.bbb }`, ["123", `123.bbb`]],
            [`{ 123, 123.456.bbb }`, ["123", `123.456.bbb`]],
            [`{ 123, "{ 123, 456 }" }`, ["123", `"{ 123, 456 }"`]],
            [`{ 123, { 123, "456" } }`, ["123", `{ 123, "456" }`]],
            [`{ 123, { aaa: 123, bbb: "456" } }`, ["123", `{ aaa: 123, bbb: "456" }`]],
            [`{ 123, {123,456}.^[2 + 5].test(123) }`, ["123", `{123,456}.^[2 + 5].test(123)`]],
            [`#COLLECTION.join({ 123, 456 }, "|")`, null],
        ])("should parse: %s to: %s", (input, output) => {
            expect(parseToList(input)).toEqual(output);
        });
    });

    describe("parseToObject", () => {
        it.each([
            [`aaa`, null],
            [`"aaa"`, null],
            [`{}`, null],
            [`{:}`, {}],
            [`#AGG.map({})`, null],
            [`#AGG.map({:})`, {}],
            [`#AGG2.map({})`, null],
            [`{aaa:123}`, { aaa: "123" }],
            [`#AGG.map({aaa:123})`, { aaa: "123" }],
            [`#AGG.map({ aaa:123, bbb:456 })`, { aaa: "123", bbb: "456" }],
            [`{ aaa: 123, bbb: "456" }`, { aaa: "123", bbb: `"456"` }],
            [`{ a: 123, b c: 123 }`, { a: "123" }],
            [`{ aaa: 1 2 3 }`, { aaa: "1" }],
            [`{ "a a a": 123 }`, { "a a a": "123" }],
            [`{ aaa: #aaa.bbb }`, { aaa: "#aaa.bbb" }],
            [`{ aaa: aaa.bbb }`, { aaa: "aaa.bbb" }],
            [`{ aaa: "aaa bbb" }`, { aaa: `"aaa bbb"` }],
            [`{ aaa: {} }`, { aaa: `{}` }],
            [`{ aaa: { bbb: 123, ccc: "456" } }`, { aaa: `{ bbb: 123, ccc: "456" }` }],
            [`{ aaa: { 123, "456" } }`, { aaa: `{ 123, "456" }` }],
            [`{ aaa: "{ 123, '456' }" }`, { aaa: `"{ 123, '456' }"` }],
            [`{ aaa: '{ 123, "456" }' }`, { aaa: `'{ 123, "456" }'` }],
            [`{ aaa: '{ 123, "456" }' }`, { aaa: `'{ 123, "456" }'` }],
            [`{ aaa:\n { 123,\n "456" } }`, { aaa: `{ 123,\n "456" }` }],
            [`{ aaa: {123,456}.![2 + 5] }`, { aaa: "{123,456}.![2 + 5]" }],
            [`{ aaa: {123,456}.![2 + 5], bbb: 123 }`, { aaa: `{123,456}.![2 + 5]`, bbb: `123` }],
            [`{ aaa: {123,456}.?[2 + 5] }`, { aaa: `{123,456}.?[2 + 5]` }],
            [`{ aaa: {123,456}.^[2 + 5] }`, { aaa: `{123,456}.^[2 + 5]` }],
            [`{ aaa: {123,456}.^[2 + 5].test(123) }`, { aaa: `{123,456}.^[2 + 5].test(123)` }],
            [`{ aaa: 123 + 456 }`, { aaa: `123 + 456` }],
        ])("should parse: %s to: %s", (input, output) => {
            expect(parseToObject(input)).toEqual(output);
        });
    });
});
