import { fireEvent, render, screen } from "@testing-library/react";
import React from "react";

import { NuThemeProvider } from "../../containers/theme/nuThemeProvider";
import { treeNodeMatchesFilter } from "../builderComponents/typeUtils";
import { DataMapper } from "./DataMapper";

// Mock Redux store dependency inside useDataMapper
jest.mock("../../store/storeHelpers", () => ({
    useAppSelector: jest.fn().mockReturnValue(null),
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

// Mock Ace-editor-based sub-components
jest.mock("./FieldRow", () => ({
    FieldRow: ({ field, onRemove, onMoveUp, onMoveDown }: any) => (
        <div data-testid={`field-row-${field.name || field.id}`}>
            <span>{field.name || "(unnamed)"}</span>
            <button data-testid={`remove-${field.id}`} onClick={onRemove}>
                Remove
            </button>
            <button data-testid={`move-up-${field.id}`} onClick={onMoveUp}>
                Up
            </button>
            <button data-testid={`move-down-${field.id}`} onClick={onMoveDown}>
                Down
            </button>
        </div>
    ),
}));

jest.mock("../builderComponents/ContextTreeNode", () => ({
    ContextTreeNode: ({ name, onSelect }: any) => (
        <div data-testid={`tree-${name}`} onClick={() => onSelect(name)}>
            {name}
        </div>
    ),
}));

function renderDataMapper(props: React.ComponentProps<typeof DataMapper> = {}) {
    return render(
        <NuThemeProvider>
            <DataMapper {...props} />
        </NuThemeProvider>,
    );
}

// ─── rendering ────────────────────────────────────────────────────────────────

describe("rendering", () => {
    it("renders without crashing", () => {
        renderDataMapper();
        expect(screen.getByText("Context Variables")).toBeInTheDocument();
        expect(screen.getByText("Target Record")).toBeInTheDocument();
    });

    it("shows 'Data Mapper' heading in standalone mode (no onInsert)", () => {
        renderDataMapper();
        expect(screen.getByRole("heading", { name: /Data Mapper/i })).toBeInTheDocument();
    });

    it("does not show standalone heading in embedded mode", () => {
        renderDataMapper({ onInsert: jest.fn() });
        expect(screen.queryByRole("heading", { name: /Data Mapper/i })).toBeNull();
    });

    it("shows Apply button in embedded mode", () => {
        renderDataMapper({ onInsert: jest.fn() });
        expect(screen.getByRole("button", { name: /apply/i })).toBeInTheDocument();
    });

    it("does not show Apply button in standalone mode", () => {
        renderDataMapper();
        expect(screen.queryByRole("button", { name: /apply/i })).toBeNull();
    });

    it("shows 'No output fields yet' empty state when embedded with no expression", () => {
        renderDataMapper({ onInsert: jest.fn() });
        expect(screen.getByText(/No output fields yet/i)).toBeInTheDocument();
    });

    it("shows Expression output panel", () => {
        renderDataMapper();
        expect(screen.getByText("Expression")).toBeInTheDocument();
    });
});

// ─── initialExpression ────────────────────────────────────────────────────────

describe("initialExpression", () => {
    it("parses initialExpression and renders matching field rows", () => {
        renderDataMapper({
            onInsert: jest.fn(),
            initialExpression: "{ city: #input.city, country: #input.country }",
        });
        expect(screen.getByTestId("field-row-city")).toBeInTheDocument();
        expect(screen.getByTestId("field-row-country")).toBeInTheDocument();
    });
});

// ─── Add Field button ─────────────────────────────────────────────────────────

describe("Add Field button", () => {
    it("clicking Add Field appends a new field row", () => {
        renderDataMapper({ onInsert: jest.fn() });
        expect(screen.queryByTestId(/^field-row-/)).toBeNull();
        fireEvent.click(screen.getByRole("button", { name: /add field/i }));
        expect(screen.getByTestId(/^field-row-/)).toBeInTheDocument();
    });

    it("each click adds one more field", () => {
        renderDataMapper({ onInsert: jest.fn() });
        fireEvent.click(screen.getByRole("button", { name: /add field/i }));
        fireEvent.click(screen.getByRole("button", { name: /add field/i }));
        expect(screen.getAllByTestId(/^field-row-/).length).toBe(2);
    });
});

// ─── Auto-map button ──────────────────────────────────────────────────────────

describe("Auto-map button", () => {
    it("Auto-map button is visible", () => {
        renderDataMapper();
        expect(screen.getByText("Auto-map")).toBeInTheDocument();
    });
});

// ─── context variables panel ──────────────────────────────────────────────────

describe("context variables panel", () => {
    it("renders tree nodes from initialContext", () => {
        // Provide initialContext directly so enrichedContext is populated without
        // depending on typingResultToSample to return a non-null sample value.
        renderDataMapper({
            initialContext: { input: { city: "Warsaw" }, output: {} },
        });
        expect(screen.getByTestId("tree-#input")).toBeInTheDocument();
        expect(screen.getByTestId("tree-#output")).toBeInTheDocument();
    });

    it("filters tree nodes based on search input", () => {
        (treeNodeMatchesFilter as jest.Mock).mockImplementation((_name: string, _val: unknown, filter: string) => {
            return _name.includes(filter);
        });

        renderDataMapper({
            initialContext: { input: {}, output: {} },
        });

        fireEvent.change(screen.getByPlaceholderText(/search/i), { target: { value: "input" } });

        expect(screen.getByTestId("tree-#input")).toBeInTheDocument();
        expect(screen.queryByTestId("tree-#output")).toBeNull();

        // restore
        (treeNodeMatchesFilter as jest.Mock).mockReturnValue(true);
    });
});

// ─── Apply button ─────────────────────────────────────────────────────────────

describe("Apply button", () => {
    it("calls onInsert with the current SpEL expression", () => {
        const onInsert = jest.fn();
        renderDataMapper({
            onInsert,
            initialExpression: "{ city: #input.city }",
        });
        fireEvent.click(screen.getByRole("button", { name: /apply/i }));
        expect(onInsert).toHaveBeenCalledTimes(1);
        expect(onInsert).toHaveBeenCalledWith(expect.stringContaining("city"));
    });
});

// ─── sample / topic panels toggled by buttons ─────────────────────────────────

describe("panel toggle buttons", () => {
    it("'From Sample' button shows the target sample panel", () => {
        renderDataMapper();
        // MUI Collapse renders children in DOM but hidden — check visibility
        expect(screen.getByPlaceholderText(/icao24/i)).not.toBeVisible();
        fireEvent.click(screen.getByRole("button", { name: /from sample/i }));
        expect(screen.getByPlaceholderText(/icao24/i)).toBeVisible();
    });
});
