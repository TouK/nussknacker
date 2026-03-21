import { stringSpelFormatter } from "./Formatter";

describe("Formatter", () => {
    describe("stringSpelFormatter", () => {
        describe("encode", () => {
            it("should encode simple string", () => {
                const result = stringSpelFormatter.encode("hello");
                expect(result).toBe('"hello"');
            });

            it("should encode empty string", () => {
                const result = stringSpelFormatter.encode("");
                expect(result).toBe('""');
            });

            it("should encode string with special characters", () => {
                const result = stringSpelFormatter.encode("hello world");
                expect(result).toBe('"hello world"');
            });

            it("should encode string with quotes", () => {
                const result = stringSpelFormatter.encode('test "quoted" text');
                expect(result).toContain("test");
                expect(result).toContain("quoted");
            });

            it("should encode string with newlines", () => {
                const result = stringSpelFormatter.encode("line1\nline2");
                expect(result).toContain("line1");
                expect(result).toContain("line2");
            });
        });

        describe("decode", () => {
            it("should decode string literal", () => {
                const result = stringSpelFormatter.decode('"hello"');
                expect(result).toBe("hello");
            });

            it("should decode single-quoted string", () => {
                const result = stringSpelFormatter.decode("'hello'");
                expect(result).toBe("hello");
            });

            it("should decode empty string", () => {
                const result = stringSpelFormatter.decode('""');
                expect(result).toBe("");
            });

            it("should return empty string for non-string expression", () => {
                const result = stringSpelFormatter.decode("42");
                expect(result).toBe("");
            });

            it("should return empty string for invalid expression", () => {
                const result = stringSpelFormatter.decode("((((");
                expect(result).toBe("");
            });

            it("should decode string with special characters", () => {
                const result = stringSpelFormatter.decode('"hello world"');
                expect(result).toBe("hello world");
            });
        });

        describe("round-trip", () => {
            it("should encode and decode back to original", () => {
                const original = "test value";
                const encoded = stringSpelFormatter.encode(original);
                const decoded = stringSpelFormatter.decode(encoded);
                expect(decoded).toBe(original);
            });

            it("should handle empty string round-trip", () => {
                const original = "";
                const encoded = stringSpelFormatter.encode(original);
                const decoded = stringSpelFormatter.decode(encoded);
                expect(decoded).toBe(original);
            });

            it("should handle special characters round-trip", () => {
                const original = "hello world tab";
                const encoded = stringSpelFormatter.encode(original);
                const decoded = stringSpelFormatter.decode(encoded);
                expect(decoded).toBe(original);
            });
        });
    });
});
