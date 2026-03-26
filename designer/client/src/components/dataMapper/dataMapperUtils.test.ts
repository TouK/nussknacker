import {
    applyTypeConversion,
    fieldsFromSample,
    genSpelFromFields,
    inferNuType,
    makeField,
    parseSpelToFields,
    splitTopLevel,
} from "./dataMapperUtils";

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

// ─── applyTypeConversion ─────────────────────────────────────────────────────

describe("applyTypeConversion", () => {
    it("returns expr unchanged when sourceType equals targetType", () => {
        expect(applyTypeConversion("#x", "Boolean", "Boolean")).toBe("#x");
    });

    it("returns expr unchanged when targetType is Any", () => {
        expect(applyTypeConversion("#x", "String", "Any")).toBe("#x");
    });

    it("returns expr unchanged when sourceType is undefined", () => {
        expect(applyTypeConversion("#x", undefined, "Boolean")).toBe("#x");
    });

    it.each([
        ["Boolean", "#x?.toBooleanOrNull ?: false"],
        ["Integer", "#x?.toIntegerOrNull ?: 0"],
        ["Long", "#x?.toLongOrNull ?: 0"],
        ["Double", "#x?.toDoubleOrNull ?: 0.0"],
        ["Float", "#x?.toDoubleOrNull ?: 0.0"],
        ["String", "#x?.toString() ?: ''"],
        ["BigDecimal", "#x?.toBigDecimalOrNull ?: 0"],
    ] as [string, string][])("Any → %s applies extension method with default fallback", (target, expected) => {
        expect(applyTypeConversion("#x", "Any", target as never)).toBe(expected);
    });

    it("Any → Boolean uses custom defaultValue when provided", () => {
        expect(applyTypeConversion("#x", "Any", "Boolean", "true")).toBe("#x?.toBooleanOrNull ?: true");
    });

    it("Any → Double uses custom defaultValue when provided", () => {
        expect(applyTypeConversion("#x", "Any", "Double", "99.9")).toBe("#x?.toDoubleOrNull ?: 99.9");
    });

    it("ZonedDateTime → Long uses DATE conversion (not Any fallback)", () => {
        expect(applyTypeConversion("#x", "ZonedDateTime", "Long")).toBe("#DATE.toEpochMilli(#x)");
    });

    it("String → Boolean falls back to Any-style conversion", () => {
        expect(applyTypeConversion("#x", "String", "Boolean")).toBe("#x?.toBooleanOrNull ?: false");
    });

    it("String → Float falls back to Any-style toDoubleOrNull", () => {
        expect(applyTypeConversion("#x", "String", "Float")).toBe("#x?.toDoubleOrNull ?: 0.0");
    });

    it("String → ZonedDateTime uses TYPE_CONVERSIONS (takes priority over Any fallback)", () => {
        expect(applyTypeConversion("#x", "String", "ZonedDateTime")).toBe('#DATE_FORMAT.parseZonedDateTime(#x, "yyyyMMdd-HH:mm:ss.SSS")');
    });

    it("unknown source type also falls back to Any-style conversion if target has one", () => {
        expect(applyTypeConversion("#x", "List", "Boolean")).toBe("#x?.toBooleanOrNull ?: false");
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
            children: [],
            isRecord: false,
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

    it("nested record value → Map field with isRecord=true and correct children", () => {
        const result = parseSpelToFields("{ meta: { key: #x, val: #y } }");
        expect(result).toHaveLength(1);
        expect(result![0]).toMatchObject({ name: "meta", type: "Map", isRecord: true });
        expect(result![0].children).toHaveLength(2);
        expect(result![0].children[0]).toMatchObject({ name: "key", expression: "#x" });
        expect(result![0].children[1]).toMatchObject({ name: "val", expression: "#y" });
    });

    it("deeply nested record value → recursive children", () => {
        const result = parseSpelToFields("{ outer: { inner: { leaf: #x } } }");
        expect(result).toHaveLength(1);
        expect(result![0]).toMatchObject({ name: "outer", isRecord: true });
        expect(result![0].children[0]).toMatchObject({ name: "inner", isRecord: true });
        expect(result![0].children[0].children[0]).toMatchObject({ name: "leaf", expression: "#x" });
    });

    it("skips parts without a colon", () => {
        const result = parseSpelToFields("{ valid: #x, noColon }");
        expect(result).toHaveLength(1);
        expect(result![0]).toMatchObject({ name: "valid" });
    });

    it("Elvis expression splits into expression + defaultValue", () => {
        const result = parseSpelToFields("{ on_ground: #x?.toBooleanOrNull ?: false }");
        expect(result).toHaveLength(1);
        expect(result![0]).toMatchObject({ name: "on_ground", expression: "#x?.toBooleanOrNull", defaultValue: "false" });
    });

    it("Elvis with float fallback splits correctly", () => {
        const result = parseSpelToFields("{ altitude: #x?.toDoubleOrNull ?: 0.0 }");
        expect(result).toHaveLength(1);
        expect(result![0]).toMatchObject({ name: "altitude", expression: "#x?.toDoubleOrNull", defaultValue: "0.0" });
    });

    it("null-ternary format (backward compat) splits into expression + defaultValue", () => {
        const result = parseSpelToFields("{ name: #input.name != null ? #input.name : 'unknown' }");
        expect(result).toHaveLength(1);
        expect(result![0]).toMatchObject({ name: "name", expression: "#input.name", defaultValue: "'unknown'" });
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

    it("record field with children generates nested SpEL record", () => {
        const f = makeField("meta", "Map");
        f.isRecord = true;
        const c1 = makeField("k1");
        c1.expression = "#x";
        const c2 = makeField("k2");
        c2.expression = "#y";
        f.children = [c1, c2];
        expect(genSpelFromFields([f])).toBe("{\n  meta: {\n    k1: #x,\n    k2: #y\n  }\n}");
    });

    it("deeply nested record generates correct indentation", () => {
        const outer = makeField("outer", "Map");
        outer.isRecord = true;
        const inner = makeField("inner", "Map");
        inner.isRecord = true;
        const leaf = makeField("leaf");
        leaf.expression = "#x";
        inner.children = [leaf];
        outer.children = [inner];
        expect(genSpelFromFields([outer])).toBe("{\n  outer: {\n    inner: {\n      leaf: #x\n    }\n  }\n}");
    });

    it("record field with no children renders empty record {}", () => {
        const f = makeField("meta", "Map");
        f.isRecord = true;
        f.children = [];
        expect(genSpelFromFields([f])).toBe("{\n  meta: {}\n}");
    });

    it("map field with isRecord=false renders as plain expression", () => {
        const f = makeField("meta", "Map");
        f.isRecord = false;
        f.expression = "#input.meta";
        expect(genSpelFromFields([f])).toBe("{\n  meta: #input.meta\n}");
    });

    it("field with defaultValue uses Elvis format", () => {
        const f = makeField("active", "Boolean");
        f.expression = "#input.flag";
        f.defaultValue = "false";
        expect(genSpelFromFields([f])).toBe("{\n  active: #input.flag ?: false\n}");
    });

    it("field with type-converted expression and defaultValue uses Elvis", () => {
        const f = makeField("active", "Boolean");
        // After splitElvisIntoField, expression has no Elvis — defaultValue holds the fallback
        f.expression = "#outputVar[8]?.toBooleanOrNull";
        f.defaultValue = "true";
        expect(genSpelFromFields([f])).toBe("{\n  active: #outputVar[8]?.toBooleanOrNull ?: true\n}");
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

    it("all returned fields have empty expression and no children", () => {
        const result = fieldsFromSample({ x: 1 });
        expect(result[0]).toMatchObject({ expression: "", children: [], isRecord: false });
    });

    it("nested object becomes a record field with children", () => {
        const result = fieldsFromSample({ address: { city: "Warsaw", zip: "00-001" } });
        expect(result).toHaveLength(1);
        expect(result[0]).toMatchObject({ name: "address", type: "Map", isRecord: true });
        expect(result[0].children).toHaveLength(2);
        expect(result[0].children[0]).toMatchObject({ name: "city", type: "String" });
        expect(result[0].children[1]).toMatchObject({ name: "zip", type: "String" });
    });
});
