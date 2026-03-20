import {
    astToSpelString,
    createStringLiteral,
    extractSubstring,
    inlineMapToRecord,
    isInlineList,
    isInlineMap,
    isStringLiteral,
    parseSpelToAst,
} from "./spelHelpers";

describe("spelHelpers", () => {
    describe("parseSpelToAst", () => {
        it("should parse valid SpEL expression", () => {
            const ast = parseSpelToAst("#x + 1");
            expect(ast).not.toBeNull();
            expect(ast?.type).toBe("OpPlus");
        });

        it("should return null for invalid expression", () => {
            const ast = parseSpelToAst("((((");
            expect(ast).toBeNull();
        });

        it("should return null for empty string", () => {
            const ast = parseSpelToAst("");
            expect(ast).toBeNull();
        });

        it("should return null for whitespace-only string", () => {
            const ast = parseSpelToAst("   ");
            expect(ast).toBeNull();
        });

        it("should trim input before parsing", () => {
            const ast = parseSpelToAst("  42  ");
            expect(ast).not.toBeNull();
            expect(ast?.type).toBe("NumberLiteral");
        });

        it("should parse string literal", () => {
            const ast = parseSpelToAst("'hello'");
            expect(ast).not.toBeNull();
            expect(ast?.type).toBe("StringLiteral");
        });

        it("should parse inline list", () => {
            const ast = parseSpelToAst("{1, 2, 3}");
            expect(ast).not.toBeNull();
            expect(ast?.type).toBe("InlineList");
        });

        it("should parse inline map", () => {
            const ast = parseSpelToAst("{name: 'John', age: 30}");
            expect(ast).not.toBeNull();
            expect(ast?.type).toBe("InlineMap");
        });
    });

    describe("astToSpelString", () => {
        it("should convert string literal to SpEL", () => {
            const ast = createStringLiteral("hello");
            const result = astToSpelString(ast);
            expect(result).toBe('"hello"');
        });

        it("should convert null literal to SpEL", () => {
            const ast = parseSpelToAst("null");
            expect(ast).not.toBeNull();
            const result = astToSpelString(ast!);
            expect(result).toBe("null");
        });
    });

    describe("extractSubstring", () => {
        it("should extract substring using start/end positions", () => {
            const input = "#x + #y";
            const ast = parseSpelToAst(input);

            expect(ast).not.toBeNull();
            expect(ast?.type).toBe("OpPlus");

            // Type assertion after null check for cleaner test
            const opPlusAst = ast as { type: string; left: Parameters<typeof extractSubstring>[1] };
            expect(opPlusAst).toHaveProperty("left");

            const result = extractSubstring(input, opPlusAst.left);
            expect(result).toBe("#x");
        });

        it("should fallback to prettyPrint when positions unavailable", () => {
            const ast = createStringLiteral("test");
            const result = extractSubstring("unused", ast);
            expect(result).toBe('"test"');
        });
    });

    describe("extractSubstring with explicit range", () => {
        it("should extract substring by range", () => {
            const text = "hello world";
            const result = extractSubstring(text, { start: 0, end: 5 });
            expect(result).toBe("hello");
        });

        it("should trim whitespace", () => {
            const text = "  hello  ";
            const result = extractSubstring(text, { start: 0, end: 9 });
            expect(result).toBe("hello");
        });

        it("should return empty string for empty text", () => {
            const result = extractSubstring("", { start: 0, end: 0 });
            expect(result).toBe("");
        });
    });

    describe("createStringLiteral", () => {
        it("should create StringLiteral AST node", () => {
            const ast = createStringLiteral("test");
            expect(ast).toEqual({ type: "StringLiteral", value: "test" });
        });

        it("should handle empty string", () => {
            const ast = createStringLiteral("");
            expect(ast).toEqual({ type: "StringLiteral", value: "" });
        });
    });

    describe("type guards", () => {
        describe("isInlineList", () => {
            it("should return true for InlineList", () => {
                const ast = parseSpelToAst("{1, 2, 3}");
                expect(isInlineList(ast)).toBe(true);
            });

            it("should return false for non-list", () => {
                const ast = parseSpelToAst("42");
                expect(isInlineList(ast)).toBe(false);
            });

            it("should return false for null", () => {
                expect(isInlineList(null)).toBe(false);
            });
        });

        describe("isInlineMap", () => {
            it("should return true for InlineMap", () => {
                const ast = parseSpelToAst("{name: 'John'}");
                expect(isInlineMap(ast)).toBe(true);
            });

            it("should return false for non-map", () => {
                const ast = parseSpelToAst("42");
                expect(isInlineMap(ast)).toBe(false);
            });

            it("should return false for null", () => {
                expect(isInlineMap(null)).toBe(false);
            });
        });

        describe("isStringLiteral", () => {
            it("should return true for StringLiteral", () => {
                const ast = parseSpelToAst("'hello'");
                expect(isStringLiteral(ast)).toBe(true);
            });

            it("should return false for non-string", () => {
                const ast = parseSpelToAst("42");
                expect(isStringLiteral(ast)).toBe(false);
            });

            it("should return false for null", () => {
                expect(isStringLiteral(null)).toBe(false);
            });
        });
    });

    describe("inlineMapToRecord", () => {
        it("should convert InlineMap to Record", () => {
            const input = "{name: 'John', age: 30}";
            const ast = parseSpelToAst(input);
            const result = inlineMapToRecord(input, ast);
            expect(result).toEqual({ name: "'John'", age: "30" });
        });

        it("should return null for non-map", () => {
            const input = "42";
            const ast = parseSpelToAst(input);
            const result = inlineMapToRecord(input, ast);
            expect(result).toBeNull();
        });

        it("should return null for null ast", () => {
            const result = inlineMapToRecord("any", null);
            expect(result).toBeNull();
        });

        it("should handle map with expressions", () => {
            const input = "{x: #input.x, y: #input.y + 1}";
            const ast = parseSpelToAst(input);
            const result = inlineMapToRecord(input, ast);
            expect(result).toEqual({ x: "#input.x", y: "#input.y + 1" });
        });

        it("should preserve original formatting from input", () => {
            const input = "{key: 'value with spaces'}";
            const ast = parseSpelToAst(input);
            const result = inlineMapToRecord(input, ast);
            expect(result).not.toBeNull();
            expect(result).toHaveProperty("key");
            expect(result?.key).toContain("value with spaces");
        });
    });
});
