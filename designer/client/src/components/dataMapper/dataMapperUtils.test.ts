import { fieldsFromSample, genSpelFromFields, inferNuType, makeField, parseSpelToFields, splitTopLevel } from "./dataMapperUtils";
import type { MapEntryDef } from "./dataMapperUtils";

// ─── inferNuType ──────────────────────────────────────────────────────────────

describe("inferNuType", () => {
    it.each([
        [null, "String"],
        [undefined, "String"],
        [true, "Boolean"],
        [false, "Boolean"],
        [0, "Integer"],
        [42, "Integer"],
        [-100, "Integer"],
        [2147483647, "Integer"], // MAX int32
        [-2147483648, "Integer"], // MIN int32
        [2147483648, "Long"], // exceeds int32
        [-2147483649, "Long"], // below int32
        [1.5, "Double"],
        [0.001, "Double"],
        ["", "String"],
        ["hello", "String"],
        [[], "List"],
        [[1, 2, 3], "List"],
        [{}, "Map"],
        [{ a: 1 }, "Map"],
    ] as [unknown, string][])("inferNuType(%p) → %s", (input, expected) => {
        expect(inferNuType(input)).toBe(expected);
    });
});

// ─── splitTopLevel ────────────────────────────────────────────────────────────

describe("splitTopLevel", () => {
    it("single entry", () => {
        expect(splitTopLevel("a: 1")).toEqual(["a: 1"]);
    });

    it("two comma-separated entries", () => {
        expect(splitTopLevel("a: 1, b: 2")).toEqual(["a: 1", "b: 2"]);
    });

    it("three comma-separated entries", () => {
        expect(splitTopLevel("a: 1, b: 2, c: 3")).toEqual(["a: 1", "b: 2", "c: 3"]);
    });

    it("comma inside braces is not a split point", () => {
        expect(splitTopLevel("a: {x: 1, y: 2}, b: 3")).toEqual(["a: {x: 1, y: 2}", "b: 3"]);
    });

    it("comma inside parens is not a split point", () => {
        expect(splitTopLevel("a: fn(1, 2), b: 3")).toEqual(["a: fn(1, 2)", "b: 3"]);
    });

    it("comma inside brackets is not a split point", () => {
        expect(splitTopLevel("a: [1, 2], b: 3")).toEqual(["a: [1, 2]", "b: 3"]);
    });

    it("doubly nested braces", () => {
        expect(splitTopLevel("a: {{x: 1, y: 2}}, b: 3")).toEqual(["a: {{x: 1, y: 2}}", "b: 3"]);
    });

    it("empty string returns empty array", () => {
        expect(splitTopLevel("")).toEqual([]);
    });

    it("whitespace only returns empty array", () => {
        expect(splitTopLevel("   ")).toEqual([]);
    });
});

// ─── parseSpelToFields ────────────────────────────────────────────────────────

describe("parseSpelToFields", () => {
    it("returns null for plain (non-record) expression", () => {
        expect(parseSpelToFields("#x.field")).toBeNull();
        expect(parseSpelToFields("true")).toBeNull();
        expect(parseSpelToFields("")).toBeNull();
    });

    it("returns empty array for empty SpEL record {}", () => {
        expect(parseSpelToFields("{}")).toEqual([]);
    });

    it("parses single field with SpEL expression", () => {
        const result = parseSpelToFields("{ name: #input.name }");
        expect(result).toHaveLength(1);
        expect(result![0]).toMatchObject({
            name: "name",
            expression: "#input.name",
            type: "Any",
            mapEntries: [],
            useMapBuilder: false,
        });
    });

    it("parses multiple fields preserving order", () => {
        const result = parseSpelToFields("{ first: #a.first, last: #a.last }");
        expect(result).toHaveLength(2);
        expect(result![0]).toMatchObject({ name: "first", expression: "#a.first" });
        expect(result![1]).toMatchObject({ name: "last", expression: "#a.last" });
    });

    it("null value creates empty field with type Any", () => {
        const result = parseSpelToFields("{ id: null }");
        expect(result).toHaveLength(1);
        expect(result![0]).toMatchObject({ name: "id", expression: "", type: "Any" });
    });

    it("nested record value → Map field with useMapBuilder=true and correct entries", () => {
        const result = parseSpelToFields("{ meta: { key: #x, val: #y } }");
        expect(result).toHaveLength(1);
        expect(result![0]).toMatchObject({ name: "meta", type: "Map", useMapBuilder: true });
        expect(result![0].mapEntries).toHaveLength(2);
        expect(result![0].mapEntries[0]).toMatchObject({ key: "key", expression: "#x" });
        expect(result![0].mapEntries[1]).toMatchObject({ key: "val", expression: "#y" });
    });

    it("skips parts without a colon", () => {
        const result = parseSpelToFields("{ valid: #x, noColon }");
        expect(result).toHaveLength(1);
        expect(result![0]).toMatchObject({ name: "valid" });
    });

    it("all fields receive unique numeric ids", () => {
        const result = parseSpelToFields("{ a: #x, b: #y }");
        expect(result).toHaveLength(2);
        expect(typeof result![0].id).toBe("number");
        expect(typeof result![1].id).toBe("number");
        expect(result![0].id).not.toBe(result![1].id);
    });
});

// ─── genSpelFromFields ────────────────────────────────────────────────────────

describe("genSpelFromFields", () => {
    it("empty fields → {\\n\\n}", () => {
        expect(genSpelFromFields([])).toBe("{\n\n}");
    });

    it("field with no expression → null placeholder", () => {
        const f = makeField("city");
        expect(genSpelFromFields([f])).toBe("{\n  city: null\n}");
    });

    it("field with expression", () => {
        const f = makeField("city");
        f.expression = "#input.city";
        expect(genSpelFromFields([f])).toBe("{\n  city: #input.city\n}");
    });

    it("multiple fields joined with commas on separate lines", () => {
        const f1 = makeField("a");
        f1.expression = "#x";
        const f2 = makeField("b");
        f2.expression = "#y";
        expect(genSpelFromFields([f1, f2])).toBe("{\n  a: #x,\n  b: #y\n}");
    });

    it("map field with entries generates nested record", () => {
        const f = makeField("meta", "Map");
        f.useMapBuilder = true;
        f.mapEntries = [
            { id: 1, key: "k1", expression: "#x" },
            { id: 2, key: "k2", expression: "#y" },
        ] satisfies MapEntryDef[];
        expect(genSpelFromFields([f])).toBe("{\n  meta: {\n    k1: #x,\n    k2: #y\n  }\n}");
    });

    it("map entries with empty keys are skipped", () => {
        const f = makeField("meta", "Map");
        f.useMapBuilder = true;
        f.mapEntries = [
            { id: 1, key: "", expression: "#x" },
            { id: 2, key: "k2", expression: "#y" },
        ] satisfies MapEntryDef[];
        const out = genSpelFromFields([f]);
        expect(out).toContain("k2: #y");
        expect(out).not.toMatch(/^\s*: #x/m);
    });

    it("map entry with empty expression uses null placeholder", () => {
        const f = makeField("meta", "Map");
        f.useMapBuilder = true;
        f.mapEntries = [{ id: 1, key: "k", expression: "" }] satisfies MapEntryDef[];
        expect(genSpelFromFields([f])).toContain("k: null");
    });

    it("map field with useMapBuilder=false renders as plain expression", () => {
        const f = makeField("meta", "Map");
        f.useMapBuilder = false;
        f.expression = "#input.meta";
        expect(genSpelFromFields([f])).toBe("{\n  meta: #input.meta\n}");
    });

    it("map field with useMapBuilder=true but empty entries falls back to null", () => {
        const f = makeField("meta", "Map");
        f.useMapBuilder = true;
        f.mapEntries = [];
        // useMapBuilder=true but no entries: condition `f.useMapBuilder && f.mapEntries.length > 0` is false
        expect(genSpelFromFields([f])).toBe("{\n  meta: null\n}");
    });
});

// ─── fieldsFromSample ─────────────────────────────────────────────────────────

describe("fieldsFromSample", () => {
    it.each([null, "string", 42, [1, 2]] as unknown[])("returns empty array for non-object: %p", (input) => {
        expect(fieldsFromSample(input)).toEqual([]);
    });

    it("returns one entry per key with inferred type", () => {
        const result = fieldsFromSample({
            name: "Alice",
            age: 30,
            active: true,
            score: 1.5,
            tags: ["a"],
            meta: {},
        });
        expect(result).toHaveLength(6);
        expect(result.find((f) => f.name === "name")).toMatchObject({ type: "String" });
        expect(result.find((f) => f.name === "age")).toMatchObject({ type: "Integer" });
        expect(result.find((f) => f.name === "active")).toMatchObject({ type: "Boolean" });
        expect(result.find((f) => f.name === "score")).toMatchObject({ type: "Double" });
        expect(result.find((f) => f.name === "tags")).toMatchObject({ type: "List" });
        expect(result.find((f) => f.name === "meta")).toMatchObject({ type: "Map" });
    });

    it("all returned fields have empty expression and no mapEntries", () => {
        const result = fieldsFromSample({ x: 1 });
        expect(result[0]).toMatchObject({ expression: "", mapEntries: [], useMapBuilder: false });
    });
});
