import { act, renderHook } from "@testing-library/react";

import { useDataMapper } from "./useDataMapper";

jest.mock("../../store/storeHelpers", () => ({
    useAppSelector: jest.fn().mockReturnValue(null),
}));

jest.mock("../../http/HttpService/instance", () => ({
    default: { validateNode: jest.fn() },
}));

jest.mock("../graph/node-modal/NodeDetailsContent/selectors", () => ({
    getProcessName: "getProcessName",
    getProcessProperties: "getProcessProperties",
}));

jest.mock("../builderComponents/typeUtils", () => ({
    toNullSafe: (path: string) => path,
    typingResultToSample: jest.fn().mockReturnValue(null),
    treeNodeMatchesFilter: jest.fn().mockReturnValue(true),
}));

// ─── initial state ────────────────────────────────────────────────────────────

describe("initial state", () => {
    it("embedded + no expression → empty fields", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        expect(result.current.fields).toEqual([]);
    });

    it("not embedded + no expression → INITIAL_FIELDS (14 predefined fields)", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: false }));
        expect(result.current.fields.length).toBeGreaterThan(0);
        expect(result.current.fields[0]).toMatchObject({ name: "icao24", type: "String" });
    });

    it("initialExpression is parsed into fields", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true, initialExpression: "{ city: #input.city }" }));
        expect(result.current.fields).toHaveLength(1);
        expect(result.current.fields[0]).toMatchObject({ name: "city", expression: "#input.city" });
    });

    it("unparseable initialExpression falls back to empty fields (embedded)", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true, initialExpression: "#invalid.not.a.record" }));
        expect(result.current.fields).toEqual([]);
    });

    it("panel toggles start as closed", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        expect(result.current.showTargetSample).toBe(false);
        expect(result.current.showContextSample).toBe(false);
        expect(result.current.showTopicPicker).toBe(false);
    });
});

// ─── addField ─────────────────────────────────────────────────────────────────

describe("addField", () => {
    it("appends a new empty field", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addField());
        expect(result.current.fields).toHaveLength(1);
        expect(result.current.fields[0]).toMatchObject({ name: "", expression: "", type: "String" });
    });

    it("each call appends one more field", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => {
            result.current.addField();
            result.current.addField();
        });
        expect(result.current.fields).toHaveLength(2);
    });
});

// ─── removeField ──────────────────────────────────────────────────────────────

describe("removeField", () => {
    it("removes the field with the given id", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addField());
        const id = result.current.fields[0].id;
        act(() => result.current.removeField(id));
        expect(result.current.fields).toHaveLength(0);
    });

    it("clears selField when removing the selected field", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addField());
        const id = result.current.fields[0].id;
        act(() => result.current.setSelField(id));
        expect(result.current.selField).toBe(id);
        act(() => result.current.removeField(id));
        expect(result.current.selField).toBeNull();
    });

    it("selField unchanged when removing a different field", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => {
            result.current.addField();
            result.current.addField();
        });
        const [id1, id2] = result.current.fields.map((f) => f.id);
        act(() => result.current.setSelField(id1));
        act(() => result.current.removeField(id2));
        expect(result.current.selField).toBe(id1);
    });
});

// ─── updateField ──────────────────────────────────────────────────────────────

describe("updateField", () => {
    it("updates the name property", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addField());
        const id = result.current.fields[0].id;
        act(() => result.current.updateField(id, "name", "myField"));
        expect(result.current.fields[0].name).toBe("myField");
    });

    it("updates the expression property", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addField());
        const id = result.current.fields[0].id;
        act(() => result.current.updateField(id, "expression", "#input.x"));
        expect(result.current.fields[0].expression).toBe("#input.x");
    });

    it("does not affect other fields", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => {
            result.current.addField();
            result.current.addField();
        });
        const [id1] = result.current.fields.map((f) => f.id);
        act(() => result.current.updateField(id1, "name", "changed"));
        expect(result.current.fields[1].name).toBe("");
    });
});

// ─── moveField ────────────────────────────────────────────────────────────────

describe("moveField", () => {
    it("moves field down by 1", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => {
            result.current.addField();
            result.current.addField();
        });
        const [id1, id2] = result.current.fields.map((f) => f.id);
        act(() => result.current.moveField(id1, 1));
        expect(result.current.fields[0].id).toBe(id2);
        expect(result.current.fields[1].id).toBe(id1);
    });

    it("moves field up by 1", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => {
            result.current.addField();
            result.current.addField();
        });
        const [id1, id2] = result.current.fields.map((f) => f.id);
        act(() => result.current.moveField(id2, -1));
        expect(result.current.fields[0].id).toBe(id2);
        expect(result.current.fields[1].id).toBe(id1);
    });

    it("does not move the first field further up", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => {
            result.current.addField();
            result.current.addField();
        });
        const [id1, id2] = result.current.fields.map((f) => f.id);
        act(() => result.current.moveField(id1, -1));
        expect(result.current.fields[0].id).toBe(id1);
        expect(result.current.fields[1].id).toBe(id2);
    });

    it("does not move the last field further down", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => {
            result.current.addField();
            result.current.addField();
        });
        const [id1, id2] = result.current.fields.map((f) => f.id);
        act(() => result.current.moveField(id2, 1));
        expect(result.current.fields[0].id).toBe(id1);
        expect(result.current.fields[1].id).toBe(id2);
    });
});

// ─── addMapEntry ──────────────────────────────────────────────────────────────

describe("addMapEntry", () => {
    it("adds an empty map entry to the specified field", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addField());
        const id = result.current.fields[0].id;
        act(() => result.current.addMapEntry(id));
        expect(result.current.fields[0].mapEntries).toHaveLength(1);
        expect(result.current.fields[0].mapEntries[0]).toMatchObject({ key: "", expression: "" });
    });

    it("does not affect other fields' map entries", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => {
            result.current.addField();
            result.current.addField();
        });
        const [id1] = result.current.fields.map((f) => f.id);
        act(() => result.current.addMapEntry(id1));
        expect(result.current.fields[1].mapEntries).toHaveLength(0);
    });
});

// ─── spelOutput ───────────────────────────────────────────────────────────────

describe("spelOutput", () => {
    it("generates empty record for no fields", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        expect(result.current.spelOutput()).toBe("{\n\n}");
    });

    it("generates SpEL reflecting current field state", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addField());
        const id = result.current.fields[0].id;
        act(() => result.current.updateField(id, "name", "city"));
        act(() => result.current.updateField(id, "expression", "#input.city"));
        expect(result.current.spelOutput()).toBe("{\n  city: #input.city\n}");
    });

    it("reflects parsed initialExpression", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true, initialExpression: "{ city: #input.city }" }));
        expect(result.current.spelOutput()).toBe("{\n  city: #input.city\n}");
    });
});

// ─── mappedCount ──────────────────────────────────────────────────────────────

describe("mappedCount", () => {
    it("counts fields with a non-empty expression", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => {
            result.current.addField();
            result.current.addField();
        });
        const [id1] = result.current.fields.map((f) => f.id);
        act(() => result.current.updateField(id1, "expression", "#x"));
        expect(result.current.mappedCount).toBe(1);
    });

    it("counts map fields with entries as mapped", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addField());
        const id = result.current.fields[0].id;
        act(() => result.current.updateField(id, "useMapBuilder", true));
        act(() => result.current.addMapEntry(id));
        expect(result.current.mappedCount).toBe(1);
    });
});

// ─── applyTargetSample ────────────────────────────────────────────────────────

describe("applyTargetSample", () => {
    it("replace mode replaces all fields from sample object", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true, initialExpression: "{ a: #x }" }));
        act(() => {
            result.current.applyTargetSample({ name: "Alice", age: 30 }, "replace");
        });
        expect(result.current.fields).toHaveLength(2);
        expect(result.current.fields.find((f) => f.name === "name")).toBeTruthy();
        expect(result.current.fields.find((f) => f.name === "age")).toBeTruthy();
        expect(result.current.fields.find((f) => f.name === "a")).toBeFalsy();
    });

    it("merge mode adds new fields without removing existing ones", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true, initialExpression: "{ a: #x }" }));
        act(() => {
            result.current.applyTargetSample({ b: "new" }, "merge");
        });
        const names = result.current.fields.map((f) => f.name);
        expect(names).toContain("a");
        expect(names).toContain("b");
    });

    it("merge mode does not duplicate fields that already exist", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true, initialExpression: "{ a: #x }" }));
        act(() => {
            result.current.applyTargetSample({ a: "same", b: "new" }, "merge");
        });
        const names = result.current.fields.map((f) => f.name);
        expect(names.filter((n) => n === "a")).toHaveLength(1);
    });

    it("returns error message for non-object sample (array)", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        let error: string | null = null;
        act(() => {
            error = result.current.applyTargetSample([], "replace");
        });
        expect(typeof error).toBe("string");
        expect(error!.length).toBeGreaterThan(0);
    });

    it("returns null on success", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        let error: string | null = "initial";
        act(() => {
            error = result.current.applyTargetSample({ x: 1 }, "replace");
        });
        expect(error).toBeNull();
    });
});

// ─── applyContextSample ───────────────────────────────────────────────────────

describe("applyContextSample", () => {
    it("updates enrichedContext from a valid object", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => {
            result.current.applyContextSample({ myVar: { x: 1 } }, "replace");
        });
        expect(result.current.enrichedContext).toMatchObject({ myVar: { x: 1 } });
    });

    it("returns error message for array input", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        let error: string | null = null;
        act(() => {
            error = result.current.applyContextSample([1, 2], "replace");
        });
        expect(typeof error).toBe("string");
    });

    it("returns error message for null input", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        let error: string | null = null;
        act(() => {
            error = result.current.applyContextSample(null, "replace");
        });
        expect(typeof error).toBe("string");
    });

    it("returns null on success", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        let error: string | null = "initial";
        act(() => {
            error = result.current.applyContextSample({ v: {} }, "replace");
        });
        expect(error).toBeNull();
    });
});

// ─── addFieldFromDrop ─────────────────────────────────────────────────────────

describe("addFieldFromDrop", () => {
    it("creates a new field using the last path segment as name", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addFieldFromDrop("input.user.name"));
        expect(result.current.fields).toHaveLength(1);
        expect(result.current.fields[0]).toMatchObject({
            name: "name",
            expression: "input.user.name",
        });
    });

    it("strips ? from field name when path uses optional chaining syntax", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addFieldFromDrop("input.user?.name"));
        expect(result.current.fields[0].name).toBe("name");
    });
});

// ─── onDrop ───────────────────────────────────────────────────────────────────

describe("onDrop", () => {
    it("sets expression on the target field", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addField());
        const id = result.current.fields[0].id;
        act(() => result.current.onDrop("input.city", id));
        expect(result.current.fields[0].expression).toBe("input.city");
    });

    it("fills in name from last path segment when field has no name", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addField());
        const id = result.current.fields[0].id;
        act(() => result.current.onDrop("input.city", id));
        expect(result.current.fields[0].name).toBe("city");
    });

    it("does not overwrite an existing name", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addField());
        const id = result.current.fields[0].id;
        act(() => result.current.updateField(id, "name", "myName"));
        act(() => result.current.onDrop("input.city", id));
        expect(result.current.fields[0].name).toBe("myName");
    });
});
