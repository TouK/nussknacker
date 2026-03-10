import { act, fireEvent, render, screen } from "@testing-library/react";
import React from "react";

import { NuThemeProvider } from "../../containers/theme/nuThemeProvider";
import { ConditionBuilder } from "./ConditionBuilder";

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

// Mock Ace-editor-based sub-components so we don't need a real DOM canvas
jest.mock("./ConditionRow", () => ({
    ConditionRow: ({ condition, index, onRemove, onUpdate, isLast }: any) => (
        <div data-testid={`condition-row-${index}`}>
            <input
                data-testid={`left-input-${index}`}
                value={condition.left}
                onChange={(e) => onUpdate(condition.id, "left", e.target.value)}
            />
            <button data-testid={`remove-${index}`} onClick={() => onRemove(condition.id)}>
                {isLast ? "Clear condition" : "Remove condition"}
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

function renderConditionBuilder(props: React.ComponentProps<typeof ConditionBuilder> = {}) {
    return render(
        <NuThemeProvider>
            <ConditionBuilder {...props} />
        </NuThemeProvider>,
    );
}

// ─── rendering ────────────────────────────────────────────────────────────────

describe("rendering", () => {
    it("renders without crashing", () => {
        renderConditionBuilder();
        expect(screen.getByText("Conditions")).toBeInTheDocument();
    });

    it("shows a single empty condition row by default", () => {
        renderConditionBuilder();
        expect(screen.getByTestId("condition-row-0")).toBeInTheDocument();
        expect(screen.queryByTestId("condition-row-1")).toBeNull();
    });

    it("shows Context Variables panel", () => {
        renderConditionBuilder();
        expect(screen.getByText("Context Variables")).toBeInTheDocument();
    });

    it("shows Expression preview panel", () => {
        renderConditionBuilder();
        expect(screen.getByText("Expression preview")).toBeInTheDocument();
    });

    it("shows Apply button when onInsert is provided", () => {
        renderConditionBuilder({ onInsert: jest.fn() });
        expect(screen.getByRole("button", { name: /apply/i })).toBeInTheDocument();
    });

    it("does not show Apply button without onInsert", () => {
        renderConditionBuilder();
        expect(screen.queryByRole("button", { name: /apply/i })).toBeNull();
    });
});

// ─── initialExpression ────────────────────────────────────────────────────────

describe("initialExpression", () => {
    it("parses initialExpression and shows correct number of condition rows", () => {
        renderConditionBuilder({
            initialExpression: "#a > 0 && #b == null",
        });
        expect(screen.getByTestId("condition-row-0")).toBeInTheDocument();
        expect(screen.getByTestId("condition-row-1")).toBeInTheDocument();
    });

    it("shows tooComplex warning when expression cannot be parsed", () => {
        // A leading combinator creates an empty first part, causing parseSpel to return null
        renderConditionBuilder({
            initialExpression: "&& #x > 0",
        });
        expect(screen.getByText(/too complex/i)).toBeInTheDocument();
    });

    it("does not show tooComplex warning for valid parseable expression", () => {
        renderConditionBuilder({
            initialExpression: "#x > 5",
        });
        expect(screen.queryByText(/too complex/i)).toBeNull();
    });
});

// ─── add / remove conditions ──────────────────────────────────────────────────

describe("add / remove conditions", () => {
    it("clicking Add condition appends a new row", () => {
        renderConditionBuilder();
        fireEvent.click(screen.getByRole("button", { name: /add condition/i }));
        expect(screen.getByTestId("condition-row-1")).toBeInTheDocument();
    });

    it("clicking Remove on the only row clears but keeps the row (isLast)", () => {
        renderConditionBuilder();
        fireEvent.click(screen.getByTestId("remove-0"));
        // Row should still be there (cleared, not removed)
        expect(screen.getByTestId("condition-row-0")).toBeInTheDocument();
        expect(screen.queryByTestId("condition-row-1")).toBeNull();
    });

    it("clicking Remove on a non-last row removes it", () => {
        renderConditionBuilder();
        fireEvent.click(screen.getByRole("button", { name: /add condition/i }));
        expect(screen.getByTestId("condition-row-1")).toBeInTheDocument();
        fireEvent.click(screen.getByTestId("remove-0"));
        expect(screen.queryByTestId("condition-row-1")).toBeNull();
    });
});

// ─── combinator selector ──────────────────────────────────────────────────────

describe("combinator selector", () => {
    it("combinator selector not shown with a single condition", () => {
        renderConditionBuilder();
        expect(screen.queryByText(/Combine with/i)).toBeNull();
    });

    it("combinator selector appears when there are two or more conditions", () => {
        renderConditionBuilder();
        fireEvent.click(screen.getByRole("button", { name: /add condition/i }));
        expect(screen.getByText(/Combine with/i)).toBeInTheDocument();
    });
});

// ─── context variables tree ───────────────────────────────────────────────────

describe("context variables tree", () => {
    it("shows 'No variables available' when variableTypes is empty", () => {
        renderConditionBuilder({ variableTypes: {} });
        expect(screen.getByText(/No variables available/i)).toBeInTheDocument();
    });

    it("renders tree nodes from variableTypes", () => {
        renderConditionBuilder({ variableTypes: { input: { refClazzName: "java.lang.String" } } });
        expect(screen.getByTestId("tree-#input")).toBeInTheDocument();
    });

    it("context filter hides non-matching nodes", () => {
        renderConditionBuilder({
            variableTypes: {
                input: { refClazzName: "java.lang.String" },
                output: { refClazzName: "java.lang.String" },
            },
        });
        const searchInput = screen.getByPlaceholderText(/search/i);
        act(() => {
            fireEvent.change(searchInput, { target: { value: "input" } });
        });
        expect(screen.getByTestId("tree-#input")).toBeInTheDocument();
        expect(screen.queryByTestId("tree-#output")).toBeNull();
    });
});

// ─── Apply button ─────────────────────────────────────────────────────────────

describe("Apply button", () => {
    it("calls onInsert with the generated SpEL when clicked", () => {
        const onInsert = jest.fn();
        renderConditionBuilder({
            onInsert,
            initialExpression: "#x > 5",
        });
        fireEvent.click(screen.getByRole("button", { name: /apply/i }));
        expect(onInsert).toHaveBeenCalledTimes(1);
        expect(onInsert).toHaveBeenCalledWith(expect.stringContaining("#x"));
    });
});
