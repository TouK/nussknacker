import { act, render } from "@testing-library/react";
import React from "react";

import { CustomCompleterAceEditor } from "../src/components/graph/node-modal/editors/expression/CustomCompleterAceEditor";
import type { CustomCompleterAceEditorProps } from "../src/components/graph/node-modal/editors/expression/CustomCompleterAceEditor";
import { ExpressionLang } from "../src/components/graph/node-modal/editors/expression/types";

// ─── Captured event plumbing ──────────────────────────────────────────────────

let capturedOnChange: (value: string) => void = jest.fn();
let capturedOnFocus: () => void = jest.fn();
const keyboardActivityListeners: Array<() => void> = [];
const changeListeners: Array<() => void> = [];
const blurListeners: Array<() => void> = [];

const mockPopupState = { activated: false };

const mockEditor = {
    completer: {
        get activated() {
            return mockPopupState.activated;
        },
        getPopup: () => ({ isOpen: mockPopupState.activated }),
    },
    on: jest.fn((event: string, cb: () => void) => {
        if (event === "keyboardActivity") keyboardActivityListeners.push(cb);
        if (event === "change") changeListeners.push(cb);
        if (event === "blur") blurListeners.push(cb);
    }),
    off: jest.fn(),
    getValue: jest.fn(() => ""),
};

jest.mock("../src/components/graph/node-modal/editors/expression/AceWithSettings", () => {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const { forwardRef, useImperativeHandle } = require("react");
    return {
        __esModule: true,
        default: forwardRef(function MockAceWithSettings(
            { onChange, onFocus }: { onChange: (v: string) => void; onFocus: () => void },
            ref: React.Ref<unknown>,
        ) {
            useImperativeHandle(ref, () => ({ editor: mockEditor }));
            capturedOnChange = onChange;
            capturedOnFocus = onFocus;
            return <div data-testid="mock-ace" />;
        }),
    };
});

jest.mock("../src/common/useUserSettings", () => ({
    useUserSettings: () => [false],
}));

jest.mock("../src/components/graph/node-modal/editors/expression/useAceEditorRangeMessages", () => ({
    useAceEditorRangeMessages: () => ({ annotations: [], markers: [], hasRangeText: false }),
}));

jest.mock("../src/components/graph/node-modal/editors/expression/AceEditorJsonBasedSnippets", () => ({
    setupAceEditorSnippets: jest.fn(),
}));

jest.mock("../src/components/modals/ValidationLabels", () => ({
    __esModule: true,
    default: ({ fieldErrors }: { fieldErrors?: Array<{ message: string }> }) => (
        <div data-testid="validation-labels">
            {(fieldErrors || []).map((e) => (
                <span key={e.message}>{e.message}</span>
            ))}
        </div>
    ),
}));

// ─── Helpers ──────────────────────────────────────────────────────────────────

function focusEditor() {
    act(() => capturedOnFocus());
}

/**
 * Simulates popup opening via the isLoading transition — the only path through
 * which completionsVisible becomes true in the component. ACE sets
 * `completer.activated` and opens the popup synchronously when completions
 * arrive, so we set mockPopupState before the rerender that drops isLoading.
 */
function openPopup(rerender: (ui: React.ReactElement) => void, props: CustomCompleterAceEditorProps) {
    mockPopupState.activated = true;
    // First render with isLoading=true so usePreviousImmediate records it.
    act(() => {
        rerender(<CustomCompleterAceEditor {...props} isLoading />);
    });
    // Drop isLoading — the effect fires (previousLoadingState=true && !isLoading=true)
    // and reads getCompletionsActivated.
    act(() => {
        rerender(<CustomCompleterAceEditor {...props} isLoading={false} />);
    });
    act(() => {
        jest.runAllTimers();
    });
}

function closePopupViaKeyboard(rerender: (ui: React.ReactElement) => void, props: CustomCompleterAceEditorProps) {
    mockPopupState.activated = false;
    act(() => {
        keyboardActivityListeners.forEach((cb) => cb());
        jest.runAllTimers();
    });
    rerender(<CustomCompleterAceEditor {...props} />);
}

function closePopupViaClick(insertedValue: string) {
    // Clicking a suggestion inserts text (fires "change") then ACE sets activated=false.
    // No keyboard event fires.
    mockPopupState.activated = false;
    mockEditor.getValue.mockReturnValue(insertedValue);
    act(() => {
        capturedOnChange(insertedValue); // AceWithSettings onChange → setInternalValue
        changeListeners.forEach((cb) => cb()); // ACE "change" event → completionsVisible update
        jest.runAllTimers();
    });
}

function typeInEditor(value: string) {
    act(() => capturedOnChange(value));
}

function buildProps(onValueChange: jest.Mock, initialValue = ""): CustomCompleterAceEditorProps {
    return {
        inputProps: {
            value: initialValue,
            onValueChange,
            language: ExpressionLang.SpEL,
            rows: 1,
            cols: 50,
        },
        fieldErrors: [],
        showValidation: true,
    };
}

// requestId simulates the uuid injected by the Redux reducer on each NODE_VALIDATION_UPDATED.
// It must differ between `errors` and `errorsAfter` so the hook detects a new validation cycle.
const errors = [{ message: "Bad expression", description: "", typ: "", fieldName: "expression", requestId: "req-1" }];
const errorsAfter = [{ message: "Updated error", description: "", typ: "", fieldName: "expression", requestId: "req-2" }];

// ─── Tests ────────────────────────────────────────────────────────────────────

describe("CustomCompleterAceEditor", () => {
    beforeEach(() => {
        jest.useFakeTimers();
        mockPopupState.activated = false;
        keyboardActivityListeners.length = 0;
        changeListeners.length = 0;
        blurListeners.length = 0;
        mockEditor.getValue.mockReturnValue("");
        jest.clearAllMocks();
    });

    afterEach(() => {
        jest.useRealTimers();
    });

    // ─── onValueChange deferral ──────────────────────────────────────────────

    it("calls onValueChange immediately when popup is not open", () => {
        const onValueChange = jest.fn();
        render(<CustomCompleterAceEditor {...buildProps(onValueChange)} />);

        focusEditor();
        typeInEditor("#''");

        expect(onValueChange).toHaveBeenCalledWith("#''");
    });

    it("defers onValueChange while popup is open (no validation requests sent)", () => {
        const onValueChange = jest.fn();
        const props = buildProps(onValueChange);
        const { rerender } = render(<CustomCompleterAceEditor {...props} />);

        focusEditor();
        openPopup(rerender, props);
        onValueChange.mockClear();

        typeInEditor("#''");

        expect(onValueChange).not.toHaveBeenCalled();
    });

    it("flushes deferred value via onValueChange when popup closes with keyboard (Escape)", () => {
        const onValueChange = jest.fn();
        const props = buildProps(onValueChange);
        const { rerender } = render(<CustomCompleterAceEditor {...props} />);

        focusEditor();
        openPopup(rerender, props);
        typeInEditor("#''");
        onValueChange.mockClear();

        closePopupViaKeyboard(rerender, props);

        expect(onValueChange).toHaveBeenCalledWith("#''");
    });

    it("flushes deferred value via onValueChange when popup closes by clicking a suggestion", () => {
        const onValueChange = jest.fn();
        const props = buildProps(onValueChange);
        const { rerender } = render(<CustomCompleterAceEditor {...props} />);

        focusEditor();
        openPopup(rerender, props);
        onValueChange.mockClear();

        closePopupViaClick("selectedSuggestion");

        expect(onValueChange).toHaveBeenCalledWith("selectedSuggestion");
    });

    // ─── Validation error visibility ─────────────────────────────────────────

    it("shows errors when showValidation=true and isLoading=false", () => {
        const { queryByText } = render(<CustomCompleterAceEditor {...buildProps(jest.fn())} fieldErrors={errors} isLoading={false} />);

        expect(queryByText("Bad expression")).not.toBeNull();
    });

    it("shows existing errors while re-validating (isLoading=true does not hide errors)", () => {
        // Old errors stay visible while a fresh request is in flight — no blank flash.
        const { queryByText } = render(<CustomCompleterAceEditor {...buildProps(jest.fn())} fieldErrors={errors} isLoading />);

        expect(queryByText("Bad expression")).not.toBeNull();
    });

    // ─── Popup + validation interaction ──────────────────────────────────────

    it("hides errors when popup opens", () => {
        const onValueChange = jest.fn();
        const props = buildProps(onValueChange);
        const { queryByText, rerender } = render(<CustomCompleterAceEditor {...props} fieldErrors={errors} />);

        focusEditor();
        expect(queryByText("Bad expression")).not.toBeNull();

        openPopup(rerender, { ...props, fieldErrors: errors });

        expect(queryByText("Bad expression")).toBeNull();
    });

    it("keeps errors hidden after popup closes when fieldErrors have not changed", () => {
        // The snapshot was cleared on popup open and is only updated when fieldErrors change.
        // If nothing was typed (same fieldErrors), errors stay hidden after close.
        const onValueChange = jest.fn();
        const props = buildProps(onValueChange);
        const { queryByText, rerender } = render(<CustomCompleterAceEditor {...props} fieldErrors={errors} />);

        focusEditor();
        openPopup(rerender, { ...props, fieldErrors: errors });
        expect(queryByText("Bad expression")).toBeNull();

        closePopupViaKeyboard(rerender, { ...props, fieldErrors: errors });

        expect(queryByText("Bad expression")).toBeNull();
    });

    it("shows fresh errors when fieldErrors change after popup closes (keyboard dismiss)", () => {
        const onValueChange = jest.fn();
        const props = buildProps(onValueChange);
        const { queryByText, rerender } = render(<CustomCompleterAceEditor {...props} fieldErrors={errors} />);

        focusEditor();
        openPopup(rerender, { ...props, fieldErrors: errors });
        closePopupViaKeyboard(rerender, { ...props, fieldErrors: errors });
        expect(queryByText("Bad expression")).toBeNull();

        // New validation result arrives with different errors
        rerender(<CustomCompleterAceEditor {...props} fieldErrors={errorsAfter} isLoading={false} />);

        expect(queryByText("Updated error")).not.toBeNull();
        expect(queryByText("Bad expression")).toBeNull();
    });

    it("shows fresh errors when fieldErrors change after popup closes (click dismiss)", () => {
        const onValueChange = jest.fn();
        const props = buildProps(onValueChange);
        const { queryByText, rerender } = render(<CustomCompleterAceEditor {...props} fieldErrors={errors} />);

        focusEditor();
        openPopup(rerender, { ...props, fieldErrors: errors });
        closePopupViaClick("selectedSuggestion");
        expect(queryByText("Bad expression")).toBeNull();

        // New validation result arrives with no errors
        rerender(<CustomCompleterAceEditor {...props} fieldErrors={[]} isLoading={false} />);

        expect(queryByText("Bad expression")).toBeNull(); // correctly cleared
    });

    it("keeps errors hidden while popup is open even when fieldErrors change (validation finishes during popup)", () => {
        // Validation may finish while the popup is still open. The popup-open rule
        // (clear snapshot) takes priority, so errors must not flash.
        const onValueChange = jest.fn();
        const props = buildProps(onValueChange);
        const { queryByText, rerender } = render(<CustomCompleterAceEditor {...props} fieldErrors={errors} />);

        focusEditor();
        openPopup(rerender, { ...props, fieldErrors: errors });

        // Validation finishes with new errors while popup is still open
        rerender(<CustomCompleterAceEditor {...props} fieldErrors={errorsAfter} isLoading={false} />);

        expect(queryByText("Updated error")).toBeNull();
        expect(queryByText("Bad expression")).toBeNull();
    });

    it("does not show validation errors while typing with autocomplete popup open", () => {
        const onValueChange = jest.fn();
        const props = buildProps(onValueChange);
        const { queryByText, rerender } = render(<CustomCompleterAceEditor {...props} fieldErrors={[]} />);

        focusEditor();

        // User types — autocomplete popup opens
        openPopup(rerender, { ...props, fieldErrors: [] });
        typeInEditor("#''");

        // Validation finishes with errors while popup is still open — must stay hidden
        rerender(<CustomCompleterAceEditor {...props} fieldErrors={errors} isLoading={false} />);
        expect(queryByText("Bad expression")).toBeNull();

        // User dismisses popup — errors still hidden (fieldErrors unchanged since last render)
        closePopupViaKeyboard(rerender, { ...props, fieldErrors: errors });
        expect(queryByText("Bad expression")).toBeNull();

        // New validation result with different errors — snapshot updates, errors appear
        rerender(<CustomCompleterAceEditor {...props} fieldErrors={errorsAfter} isLoading={false} />);
        expect(queryByText("Updated error")).not.toBeNull();
    });
});
