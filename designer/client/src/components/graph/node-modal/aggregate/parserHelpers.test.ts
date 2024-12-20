import { parseToList, parseToObject } from "./pareserHelpers";

describe("parseHelpers", () => {
    describe("parseToList", () => {
        it.each([
            [`aaa`, null],
            [`"aaa"`, null],
            [`{`, null],
            [`{}`, null],
            [`{123}`, null],
            [`{}.xxxx`, null],
            [`{}.toString`, []],
            [`#COLLECTION.join({}, "|")`, null],
            [`#xxxx.yyyy({}, "|")`, null],
            [`{123}.toString`, ["123"]],
            [`{ 123 }.toString`, ["123"]],
            [`{123,456}.toString`, ["123", "456"]],
            [`{ 123, 456 }.toString`, ["123", "456"]],
            [`{ 123, aaa, 456 }.toString`, ["123", "aaa", "456"]],
            [`{ 123, aaa.bbb }.toString`, ["123", "aaa.bbb"]],
            [`{ 123, aaa_bbb }.toString`, ["123", "aaa_bbb"]],
            [`{ 123, aaa bbb, ccc }.toString`, ["123", "aaa", "bbb", "ccc"]],
            [`{ 123, "aaa bbb" }.toString`, ["123", `"aaa bbb"`]],
            [`{ 123, 'aaa bbb' }.toString`, ["123", `'aaa bbb'`]],
            [`{ 123, #aaa.bbb }.toString`, ["123", `#aaa.bbb`]],
            [`{ 123, 123.bbb }.toString`, ["123", `123.bbb`]],
            [`{ 123, 123.456.bbb }.toString`, ["123", `123.456.bbb`]],
            [`{ 123, "{ 123, 456 }" }.toString`, ["123", `"{ 123, 456 }"`]],
            [`{ 123, { 123, "456" } }.toString`, ["123", `{ 123, "456" }`]],
            [`{ 123, { aaa: 123, bbb: "456" } }.toString`, ["123", `{ aaa: 123, bbb: "456" }`]],
            [`{ 123, {123,456}.^[2 + 5].test(123) }.toString`, ["123", `{123,456}.^[2 + 5].test(123)`]],
            [`#COLLECTION.join({ 123, 456 }, "|")`, null],
        ])("should parse: %s to: %s", (input, output) => {
            expect(parseToList(input)).toEqual(output);
        });
    });

    describe("parseToObject", () => {
        it.each([
            [`aaa`, null],
            [`"aaa"`, null],
            [`{}`, {}],
            [`#AGG.map({})`, {}],
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
