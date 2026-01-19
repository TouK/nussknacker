import { decodeAssertionExpression, encodeAssertionExpression, type AssertionParts } from "./assertionEncoder";

describe("assertionEncoder", () => {
    describe("decodeAssertionExpression", () => {
        it("should decode simple string assertion", () => {
            const expression = "#TESTS.assertEquals('abc', 'cdf')";
            const result = decodeAssertionExpression(expression);

            expect(result).toEqual({
                assertion: "assertEquals",
                expected: "abc",
                actual: "cdf",
            });
        });

        it("should decode with mixed quotes", () => {
            const expression = "#TESTS.assertEquals('abc', \"cdf\")";
            const result = decodeAssertionExpression(expression);

            expect(result).toEqual({
                assertion: "assertEquals",
                expected: "abc",
                actual: "cdf",
            });
        });

        it("should decode with SpEL expressions", () => {
            const expression = "#TESTS.assertEquals(#obj.field, #expected.value)";
            const result = decodeAssertionExpression(expression);

            expect(result).toEqual({
                assertion: "assertEquals",
                expected: "#obj.field",
                actual: "#expected.value",
            });
        });

        it("should decode with nested parentheses", () => {
            const expression = "#TESTS.assertEquals(#func(1, 2), #other(3, 4))";
            const result = decodeAssertionExpression(expression);

            expect(result).toEqual({
                assertion: "assertEquals",
                expected: "#func(1, 2)",
                actual: "#other(3, 4)",
            });
        });

        it("should decode with curly braces in expressions", () => {
            const expression = "#TESTS.assertEquals(#{a: 1, b: 2}, #{c: 3})";
            const result = decodeAssertionExpression(expression);

            expect(result).toEqual({
                assertion: "assertEquals",
                expected: "#{a: 1, b: 2}",
                actual: "#{c: 3}",
            });
        });

        it("should decode with single argument (assertTrue)", () => {
            const expression = "#TESTS.assertTrue('result')";
            const result = decodeAssertionExpression(expression);

            expect(result).toEqual({
                assertion: "assertTrue",
                expected: "result",
                actual: "",
            });
        });

        it("should handle empty strings", () => {
            const expression = "#TESTS.assertEquals('', '')";
            const result = decodeAssertionExpression(expression);

            expect(result).toEqual({
                assertion: "assertEquals",
                expected: "",
                actual: "",
            });
        });

        it("should trim whitespace around arguments", () => {
            const expression = "#TESTS.assertEquals(  'abc'  ,  'def'  )";
            const result = decodeAssertionExpression(expression);

            expect(result?.expected).toBe("abc");
            expect(result?.actual).toBe("def");
        });

        it("should return null for invalid input", () => {
            expect(decodeAssertionExpression("")).toBeNull();
            expect(decodeAssertionExpression(null as unknown as string)).toBeNull();
            expect(decodeAssertionExpression("assertEquals('abc', 'cdf')")).toBeNull();
            expect(decodeAssertionExpression("#TESTS.assertEquals()")).toBeNull();
        });
    });

    describe("encodeAssertionExpression", () => {
        it("should encode simple string assertion", () => {
            const parts: AssertionParts = {
                assertion: "assertEquals",
                expected: "abc",
                actual: "cdf",
            };
            const result = encodeAssertionExpression(parts);

            expect(result).toBe("#TESTS.assertEquals('abc', 'cdf')");
        });

        it("should encode SpEL expressions without quoting", () => {
            const parts: AssertionParts = {
                assertion: "assertEquals",
                expected: "#obj.field",
                actual: "#expected.value",
            };
            const result = encodeAssertionExpression(parts);

            expect(result).toBe("#TESTS.assertEquals(#obj.field, #expected.value)");
        });

        it("should encode expressions with curly braces", () => {
            const parts: AssertionParts = {
                assertion: "assertEquals",
                expected: "#{a: 1, b: 2}",
                actual: "#{c: 3}",
            };
            const result = encodeAssertionExpression(parts);

            expect(result).toBe("#TESTS.assertEquals(#{a: 1, b: 2}, #{c: 3})");
        });

        it("should encode function calls", () => {
            const parts: AssertionParts = {
                assertion: "assertEquals",
                expected: "#func(1, 2)",
                actual: "#other(3, 4)",
            };
            const result = encodeAssertionExpression(parts);

            expect(result).toBe("#TESTS.assertEquals(#func(1, 2), #other(3, 4))");
        });

        it("should quote simple strings", () => {
            const parts: AssertionParts = {
                assertion: "assertEquals",
                expected: "hello",
                actual: "world",
            };
            const result = encodeAssertionExpression(parts);

            expect(result).toBe("#TESTS.assertEquals('hello', 'world')");
        });
    });

    describe("round-trip encode/decode", () => {
        it("should preserve simple strings", () => {
            const original: AssertionParts = {
                assertion: "assertEquals",
                expected: "abc",
                actual: "cdf",
            };
            const encoded = encodeAssertionExpression(original);
            const decoded = decodeAssertionExpression(encoded);

            expect(decoded).toEqual(original);
        });

        it("should preserve SpEL expressions", () => {
            const original: AssertionParts = {
                assertion: "assertEquals",
                expected: "#obj.field",
                actual: "#expected.value",
            };
            const encoded = encodeAssertionExpression(original);
            const decoded = decodeAssertionExpression(encoded);

            expect(decoded).toEqual(original);
        });

        it("should preserve curly brace expressions", () => {
            const original: AssertionParts = {
                assertion: "assertEquals",
                expected: "#{a: 1, b: 2}",
                actual: "#{c: 3}",
            };
            const encoded = encodeAssertionExpression(original);
            const decoded = decodeAssertionExpression(encoded);

            expect(decoded).toEqual(original);
        });

        it("should preserve nested function calls", () => {
            const original: AssertionParts = {
                assertion: "assertEquals",
                expected: "#func(#inner(1), 2)",
                actual: "#other(#deep(#nested(x)))",
            };
            const encoded = encodeAssertionExpression(original);
            const decoded = decodeAssertionExpression(encoded);

            expect(decoded).toEqual(original);
        });

        it("should preserve strings with commas", () => {
            const original: AssertionParts = {
                assertion: "assertEquals",
                expected: "a,b,c,d",
                actual: "x,y,z",
            };
            const encoded = encodeAssertionExpression(original);
            const decoded = decodeAssertionExpression(encoded);

            expect(decoded).toEqual(original);
        });
    });
});
