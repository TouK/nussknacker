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

    it("not embedded + no expression → INITIAL_FIELDS (predefined fields)", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: false }));
        expect(result.current.fields.length).toBeGreaterThan(0);
        expect(result.current.fields[0]).toMatchObject({ name: "id", type: "Long" });
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

// ─── addChildField ────────────────────────────────────────────────────────────

describe("addChildField", () => {
    it("adds a child field to the specified parent", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addField());
        const id = result.current.fields[0].id;
        act(() => result.current.addChildField(id));
        expect(result.current.fields[0].children).toHaveLength(1);
        expect(result.current.fields[0].children[0]).toMatchObject({ name: "", expression: "" });
    });

    it("does not affect other fields' children", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => {
            result.current.addField();
            result.current.addField();
        });
        const [id1] = result.current.fields.map((f) => f.id);
        act(() => result.current.addChildField(id1));
        expect(result.current.fields[1].children).toHaveLength(0);
    });

    it("can add child to a nested child field (arbitrary depth)", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addField());
        const parentId = result.current.fields[0].id;
        act(() => result.current.addChildField(parentId));
        const childId = result.current.fields[0].children[0].id;
        act(() => result.current.addChildField(childId));
        expect(result.current.fields[0].children[0].children).toHaveLength(1);
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

    it("counts record fields (isRecord=true) as mapped", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addField());
        const id = result.current.fields[0].id;
        act(() => result.current.updateField(id, "isRecord", true));
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

// ─── nested record: updateField ───────────────────────────────────────────────

describe("nested record: updateField", () => {
    it("updates name of a direct child field", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addField());
        const parentId = result.current.fields[0].id;
        act(() => result.current.addChildField(parentId));
        const childId = result.current.fields[0].children[0].id;
        act(() => result.current.updateField(childId, "name", "child_name"));
        expect(result.current.fields[0].children[0].name).toBe("child_name");
    });

    it("updates expression of a child at depth 2", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addField());
        const parentId = result.current.fields[0].id;
        act(() => result.current.addChildField(parentId));
        const childId = result.current.fields[0].children[0].id;
        act(() => result.current.addChildField(childId));
        const grandChildId = result.current.fields[0].children[0].children[0].id;
        act(() => result.current.updateField(grandChildId, "expression", "#input.deep"));
        expect(result.current.fields[0].children[0].children[0].expression).toBe("#input.deep");
    });

    it("does not affect sibling fields when updating a child", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addField());
        const parentId = result.current.fields[0].id;
        act(() => {
            result.current.addChildField(parentId);
            result.current.addChildField(parentId);
        });
        const [childId1] = result.current.fields[0].children.map((c) => c.id);
        act(() => result.current.updateField(childId1, "name", "changed"));
        expect(result.current.fields[0].children[1].name).toBe("");
    });
});

// ─── nested record: removeField ───────────────────────────────────────────────

describe("nested record: removeField", () => {
    it("removes a direct child field", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addField());
        const parentId = result.current.fields[0].id;
        act(() => {
            result.current.addChildField(parentId);
            result.current.addChildField(parentId);
        });
        const childId = result.current.fields[0].children[0].id;
        act(() => result.current.removeField(childId));
        expect(result.current.fields[0].children).toHaveLength(1);
        expect(result.current.fields).toHaveLength(1); // parent stays
    });

    it("removing a parent record also removes all its children", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addField());
        const parentId = result.current.fields[0].id;
        act(() => result.current.addChildField(parentId));
        act(() => result.current.removeField(parentId));
        expect(result.current.fields).toHaveLength(0);
    });

    it("removes a grandchild without affecting the rest of the tree", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addField());
        const parentId = result.current.fields[0].id;
        act(() => result.current.addChildField(parentId));
        const childId = result.current.fields[0].children[0].id;
        act(() => result.current.addChildField(childId));
        const grandChildId = result.current.fields[0].children[0].children[0].id;
        act(() => result.current.removeField(grandChildId));
        expect(result.current.fields[0].children[0].children).toHaveLength(0);
        expect(result.current.fields[0].children).toHaveLength(1);
    });
});

// ─── nested record: moveField ─────────────────────────────────────────────────

describe("nested record: moveField", () => {
    it("moves a child down within its siblings", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addField());
        const parentId = result.current.fields[0].id;
        act(() => {
            result.current.addChildField(parentId);
            result.current.addChildField(parentId);
        });
        const [c1, c2] = result.current.fields[0].children.map((c) => c.id);
        act(() => result.current.moveField(c1, 1));
        expect(result.current.fields[0].children[0].id).toBe(c2);
        expect(result.current.fields[0].children[1].id).toBe(c1);
    });

    it("moving a child does not change top-level field order", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => {
            result.current.addField();
            result.current.addField();
        });
        const [topId1, topId2] = result.current.fields.map((f) => f.id);
        act(() => result.current.addChildField(topId1));
        act(() => result.current.addChildField(topId1));
        const c1 = result.current.fields[0].children[0].id;
        act(() => result.current.moveField(c1, 1));
        expect(result.current.fields[0].id).toBe(topId1);
        expect(result.current.fields[1].id).toBe(topId2);
    });

    it("does not move a child past the last sibling", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addField());
        const parentId = result.current.fields[0].id;
        act(() => {
            result.current.addChildField(parentId);
            result.current.addChildField(parentId);
        });
        const [c1, c2] = result.current.fields[0].children.map((c) => c.id);
        act(() => result.current.moveField(c2, 1));
        expect(result.current.fields[0].children[0].id).toBe(c1);
        expect(result.current.fields[0].children[1].id).toBe(c2);
    });
});

// ─── nested record: spelOutput round-trip ─────────────────────────────────────

describe("nested record: spelOutput", () => {
    it("produces correct SpEL for a one-level nested record", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addField());
        const parentId = result.current.fields[0].id;
        act(() => result.current.updateField(parentId, "name", "address"));
        act(() => result.current.updateField(parentId, "isRecord", true));
        act(() => result.current.addChildField(parentId));
        const childId = result.current.fields[0].children[0].id;
        act(() => result.current.updateField(childId, "name", "city"));
        act(() => result.current.updateField(childId, "expression", "#input.city"));
        expect(result.current.spelOutput()).toBe("{\n  address: {\n    city: #input.city\n  }\n}");
    });

    it("round-trip: initialExpression with nested record → spelOutput unchanged", () => {
        const expr = "{\n  address: {\n    city: #input.city,\n    zip: #input.zip\n  }\n}";
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true, initialExpression: expr }));
        expect(result.current.spelOutput()).toBe(expr);
    });

    it("round-trip: deeply nested expression survives parse → generate", () => {
        const expr = "{\n  outer: {\n    inner: {\n      leaf: #x\n    }\n  }\n}";
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true, initialExpression: expr }));
        expect(result.current.spelOutput()).toBe(expr);
    });

    it("mixed flat and record fields at same level", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => {
            result.current.addField();
            result.current.addField();
        });
        const [flatId, recordId] = result.current.fields.map((f) => f.id);
        act(() => result.current.updateField(flatId, "name", "name"));
        act(() => result.current.updateField(flatId, "expression", "#input.name"));
        act(() => result.current.updateField(recordId, "name", "address"));
        act(() => result.current.updateField(recordId, "isRecord", true));
        act(() => result.current.addChildField(recordId));
        const childId = result.current.fields[1].children[0].id;
        act(() => result.current.updateField(childId, "name", "city"));
        act(() => result.current.updateField(childId, "expression", "#input.city"));
        expect(result.current.spelOutput()).toBe("{\n  name: #input.name,\n  address: {\n    city: #input.city\n  }\n}");
    });
});

// ─── nested record: onDrop ────────────────────────────────────────────────────

describe("nested record: onDrop", () => {
    it("sets expression on a nested child field", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => result.current.addField());
        const parentId = result.current.fields[0].id;
        act(() => result.current.addChildField(parentId));
        const childId = result.current.fields[0].children[0].id;
        act(() => result.current.onDrop("input.city", childId));
        expect(result.current.fields[0].children[0].expression).toBe("#input.city");
    });
});

// ─── nested record: applyTargetSample ────────────────────────────────────────

describe("nested record: applyTargetSample", () => {
    it("nested object in sample becomes a record field with children", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => {
            result.current.applyTargetSample({ name: "Alice", address: { city: "Warsaw", zip: "00-001" } }, "replace");
        });
        expect(result.current.fields).toHaveLength(2);
        const addressField = result.current.fields.find((f) => f.name === "address");
        expect(addressField).toBeTruthy();
        expect(addressField!.isRecord).toBe(true);
        expect(addressField!.children).toHaveLength(2);
        expect(addressField!.children[0]).toMatchObject({ name: "city", type: "String" });
        expect(addressField!.children[1]).toMatchObject({ name: "zip", type: "String" });
    });

    it("all child fields get unique ids", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true }));
        act(() => {
            result.current.applyTargetSample({ a: { x: 1, y: 2 }, b: { z: 3 } }, "replace");
        });
        const allIds = result.current.fields.flatMap((f) => [f.id, ...f.children.map((c) => c.id)]);
        expect(new Set(allIds).size).toBe(allIds.length);
    });
});

// ─── nested record: handleAutoMap ─────────────────────────────────────────────

describe("nested record: handleAutoMap", () => {
    it("auto-maps leaf fields inside a nested record", () => {
        const { result } = renderHook(() =>
            useDataMapper({ isEmbedded: true, initialContext: { user: { city: "Warsaw", name: "Alice" } } }),
        );
        act(() => result.current.addField());
        const parentId = result.current.fields[0].id;
        act(() => result.current.updateField(parentId, "name", "userInfo"));
        act(() => result.current.updateField(parentId, "isRecord", true));
        act(() => result.current.addChildField(parentId));
        const childId = result.current.fields[0].children[0].id;
        act(() => result.current.updateField(childId, "name", "city"));
        act(() => result.current.handleAutoMap());
        expect(result.current.fields[0].children[0].expression).toBe("#user['city']");
    });

    it("does not overwrite already-mapped nested fields", () => {
        const { result } = renderHook(() => useDataMapper({ isEmbedded: true, initialContext: { input: { name: "Alice" } } }));
        act(() => result.current.addField());
        const parentId = result.current.fields[0].id;
        act(() => result.current.updateField(parentId, "isRecord", true));
        act(() => result.current.addChildField(parentId));
        const childId = result.current.fields[0].children[0].id;
        act(() => result.current.updateField(childId, "name", "name"));
        act(() => result.current.updateField(childId, "expression", "#existing"));
        act(() => result.current.handleAutoMap());
        expect(result.current.fields[0].children[0].expression).toBe("#existing");
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
            expression: "#input.user.name",
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
        expect(result.current.fields[0].expression).toBe("#input.city");
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
