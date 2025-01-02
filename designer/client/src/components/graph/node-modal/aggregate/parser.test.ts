import { AggMapLikeParser } from "./aggMapLikeParser";

describe("AggMapLikeParser", () => {
    let parser: AggMapLikeParser;

    beforeEach(() => {
        parser = new AggMapLikeParser();
    });

    it.each([
        [`aaa`, null],
        [`"aaa"`, null],
        [`{`, null],
        [`{}`, null],
        [`{123}`, null],
        [`{}.toString`, []],
        [`#COLLECTION.join({}, "|")`, []],
        [`{123}.toString`, ["123"]],
        [`{ 123 }.toString`, ["123"]],
        [`{123,456}.toString`, ["123", "456"]],
        [`{ 123, 456 }.toString`, ["123", "456"]],
        [`{ 123, aaa, 456 }.toString`, ["123", "aaa", "456"]],
        [`{ 123, aaa.bbb }.toString`, ["123", "aaa.bbb"]],
        [`{ 123, aaa_bbb }.toString`, ["123", "aaa_bbb"]],
        [`{ 123, aaa bbb, ccc }.toString`, ["123", "aaa bbb", "ccc"]],
        [`{ 123, "aaa bbb" }.toString`, ["123", `"aaa bbb"`]],
        [`{ 123, 'aaa bbb' }.toString`, ["123", `'aaa bbb'`]],
        [`{ 123, #aaa.bbb }.toString`, ["123", `#aaa.bbb`]],
        [`{ 123, 123.bbb }.toString`, ["123", `123.bbb`]],
        [`{ 123, 123.456.bbb }.toString`, ["123", `123.456.bbb`]],
        [`{ 123, "{ 123, 456 }" }.toString`, ["123", `"{ 123, 456 }"`]],
        [`{ 123, { 123, "456" } }.toString`, ["123", `{ 123, "456" }`]],
        [`{ 123, { aaa: 123, bbb: "456" } }.toString`, ["123", `{ aaa: 123, bbb: "456" }`]],
        [`#COLLECTION.join({ 123, 456 }, "|")`, ["123", "456"]],
    ])("should parse list: %s => %s", (input, output) => {
        expect(parser.parseList(input)).toEqual(output);
    });

    it.each([
        [`aaa`, null],
        [`"aaa"`, null],
        [`{}`, {}],
        [`#AGG.map({})`, {}],
        [`{aaa:123}`, { aaa: "123" }],
        [`#AGG.map({aaa:123})`, { aaa: "123" }],
        [`{ aaa: 123, bbb: "456" }`, { aaa: "123", bbb: `"456"` }],
        [`{ a: 123, b c: 123 }`, { a: "123", "b c": "123" }],
        [`{ aaa: 1 2 3 }`, { aaa: "1 2 3" }],
        [`{ "a a a": 123 }`, { "a a a": "123" }],
        [`{ aaa: #aaa.bbb }`, { aaa: "#aaa.bbb" }],
        [`{ aaa: aaa.bbb }`, { aaa: "aaa.bbb" }],
        [`{ aaa: "aaa bbb" }`, { aaa: `"aaa bbb"` }],
        [`{ aaa: {} }`, { aaa: `{}` }],
        [`{ aaa: { bbb: 123, ccc: "456" } }`, { aaa: `{ bbb: 123, ccc: "456" }` }],
        [`{ aaa: { 123, "456" } }`, { aaa: `{ 123, "456" }` }],
        [`{ aaa: "{ 123, '456' }" }`, { aaa: `"{ 123, '456' }"` }],
    ])("should parse map: %s => %s", (input, output) => {
        expect(parser.parseObject(input)).toEqual(output);
    });
});
