import type { SpelDndContext } from "../../io/ContextTree";
import { ExpressionLang } from "./types";
import { getInsertText, jsonToSpelString } from "./useAceDndTarget";

describe("jsonToSpelString", () => {
    it("converts null", () => {
        expect(jsonToSpelString(null)).toBe("null");
    });

    it("converts string with escaping", () => {
        expect(jsonToSpelString("hello")).toBe('"hello"');
        expect(jsonToSpelString('say "hi"')).toBe('"say \\"hi\\""');
    });

    it("converts number", () => {
        expect(jsonToSpelString(42)).toBe("42");
        expect(jsonToSpelString(3.14)).toBe("3.14");
    });

    it("converts boolean", () => {
        expect(jsonToSpelString(true)).toBe("true");
        expect(jsonToSpelString(false)).toBe("false");
    });

    it("converts array", () => {
        expect(jsonToSpelString([1, "a"])).toBe('{1,"a"}');
    });

    it("converts object", () => {
        expect(jsonToSpelString({ a: 1 })).toBe("{ a: 1 }");
    });

    it("quotes object keys with spaces", () => {
        expect(jsonToSpelString({ "my key": 1 })).toBe('{ "my key": 1 }');
    });
});

describe("getInsertText", () => {
    const noOverrides = { useRecordsPrefix: false, useValueInsert: false };

    describe("useRecordsPrefix=true — Actual field DnD", () => {
        const opts = { useRecordsPrefix: true, useValueInsert: false };

        it("produces #records[N].path for SpEL", () => {
            const item: SpelDndContext = { type: "key", path: ["status"], value: "active", recordIndex: 2 };
            expect(getInsertText(item, ExpressionLang.SpEL, opts)).toBe("#records[2].status");
        });

        it("uses recordIndex from first record", () => {
            const item: SpelDndContext = { type: "key", path: ["amount"], value: 100, recordIndex: 0 };
            expect(getInsertText(item, ExpressionLang.SpEL, opts)).toBe("#records[0].amount");
        });

        it("falls back to standard path when recordIndex is undefined", () => {
            const item: SpelDndContext = { type: "key", path: ["status"], value: "active" };
            expect(getInsertText(item, ExpressionLang.SpEL, opts)).toBe("#status");
        });
    });

    describe("useValueInsert=true — Expected field DnD", () => {
        const opts = { useRecordsPrefix: false, useValueInsert: true };

        it("inserts string value as SpEL string literal", () => {
            const item: SpelDndContext = { type: "value", path: ["status"], value: "active" };
            expect(getInsertText(item, ExpressionLang.SpEL, opts)).toBe('"active"');
        });

        it("inserts numeric value as-is", () => {
            const item: SpelDndContext = { type: "value", path: ["amount"], value: 42 };
            expect(getInsertText(item, ExpressionLang.SpEL, opts)).toBe("42");
        });

        it("inserts boolean value", () => {
            const item: SpelDndContext = { type: "value", path: ["active"], value: true };
            expect(getInsertText(item, ExpressionLang.SpEL, opts)).toBe("true");
        });

        it("inserts null value", () => {
            const item: SpelDndContext = { type: "value", path: ["field"], value: null };
            expect(getInsertText(item, ExpressionLang.SpEL, opts)).toBe("null");
        });

        it("ignores recordIndex — always inserts value", () => {
            const item: SpelDndContext = { type: "value", path: ["status"], value: "done", recordIndex: 5 };
            expect(getInsertText(item, ExpressionLang.SpEL, opts)).toBe('"done"');
        });
    });

    describe("no overrides — standard behaviour", () => {
        it("inserts #path for key type in SpEL", () => {
            const item: SpelDndContext = { type: "key", path: ["input", "status"], value: "active" };
            expect(getInsertText(item, ExpressionLang.SpEL, noOverrides)).toBe('#input["status"]');
        });

        it("inserts value for value type in SpEL", () => {
            const item: SpelDndContext = { type: "value", path: ["amount"], value: 99 };
            expect(getInsertText(item, ExpressionLang.SpEL, noOverrides)).toBe("99");
        });
    });
});
