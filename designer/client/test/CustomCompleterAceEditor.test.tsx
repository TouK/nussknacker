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
    // Drop isLoading — the isLoading effect fires (previousLoadingState=true && !isLoading=true)
    // and checks getCompletionsActivated via a setTimeout.
    act(() => {
        rerender(<CustomCompleterAceEditor {...props} isLoading={false} />);
    });
    // Flush the setTimeout so setCompletionsVisible(true) runs.
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

/**
 * Simulates a full isValidating true→false cycle (as if validateNodeData fired and
 * the HTTP response arrived). This is what clears waitingForFreshValidation after
 * the autocomplete popup closes, allowing validation errors to become visible again.
 */
function simulateValidationCycle(rerender: (ui: React.ReactElement) => void, props: CustomCompleterAceEditorProps) {
    act(() => rerender(<CustomCompleterAceEditor {...props} isValidating />));
    act(() => rerender(<CustomCompleterAceEditor {...props} isValidating={false} />));
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

const errors = [{ message: "Bad expression", description: "", typ: "", fieldName: "expression" }];

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

    it("shows errors when showValidation=true and isValidating=false", () => {
        const { queryByText } = render(<CustomCompleterAceEditor {...buildProps(jest.fn())} fieldErrors={errors} isValidating={false} />);

        act(() => jest.advanceTimersByTime(100));
        expect(queryByText("Bad expression")).not.toBeNull();
    });

    it("shows existing errors while re-validating (isValidating=true does not hide errors)", () => {
        // Old errors stay visible while a fresh request is in flight — no blank flash.
        const { queryByText } = render(<CustomCompleterAceEditor {...buildProps(jest.fn())} fieldErrors={errors} isValidating />);

        act(() => jest.advanceTimersByTime(100));
        expect(queryByText("Bad expression")).not.toBeNull();
    });

    // ─── Popup + validation interaction ──────────────────────────────────────

    it("hides errors when popup opens and shows them after popup closes and validation completes", () => {
        const onValueChange = jest.fn();
        const props = buildProps(onValueChange);
        const { queryByText, rerender } = render(<CustomCompleterAceEditor {...props} fieldErrors={errors} />);

        focusEditor();
        act(() => jest.advanceTimersByTime(100));
        expect(queryByText("Bad expression")).not.toBeNull();

        // Popup opens — errors disappear after debounce
        openPopup(rerender, { ...props, fieldErrors: errors });
        act(() => jest.advanceTimersByTime(100));
        expect(queryByText("Bad expression")).toBeNull();

        // Popup closes — errors still hidden, waitingForFreshValidation blocks them
        closePopupViaKeyboard(rerender, { ...props, fieldErrors: errors });
        act(() => jest.advanceTimersByTime(100));
        expect(queryByText("Bad expression")).toBeNull();

        // Validation cycle completes after popup close — errors reappear
        simulateValidationCycle(rerender, { ...props, fieldErrors: errors });
        act(() => jest.advanceTimersByTime(100));
        expect(queryByText("Bad expression")).not.toBeNull();
    });

    it("hides errors when popup opens and shows them after clicking a suggestion and validation completes", () => {
        const onValueChange = jest.fn();
        const props = buildProps(onValueChange);
        const { queryByText, rerender } = render(<CustomCompleterAceEditor {...props} fieldErrors={errors} />);

        focusEditor();
        act(() => jest.advanceTimersByTime(100));
        expect(queryByText("Bad expression")).not.toBeNull();

        openPopup(rerender, { ...props, fieldErrors: errors });
        act(() => jest.advanceTimersByTime(100));
        expect(queryByText("Bad expression")).toBeNull();

        // Popup closes via click — errors still hidden until validation completes
        closePopupViaClick("selectedSuggestion");
        act(() => jest.advanceTimersByTime(100));
        expect(queryByText("Bad expression")).toBeNull();

        simulateValidationCycle(rerender, { ...props, fieldErrors: errors });
        act(() => jest.advanceTimersByTime(100));
        expect(queryByText("Bad expression")).not.toBeNull();
    });

    it("does not flash old errors when validation completes while popup is still open (race condition)", () => {
        // Scenario: validation finishes while popup is open (isValidating: true→false).
        // Without the !completionsVisible guard, waitingForFreshValidation would be cleared
        // immediately, causing old errors to flash as soon as the popup closes.
        const onValueChange = jest.fn();
        const props = buildProps(onValueChange);
        const { queryByText, rerender } = render(<CustomCompleterAceEditor {...props} fieldErrors={errors} />);

        focusEditor();
        openPopup(rerender, { ...props, fieldErrors: errors });

        // Validation completes while popup is still open — errors must stay hidden
        simulateValidationCycle(rerender, { ...props, fieldErrors: errors });
        act(() => jest.advanceTimersByTime(100));
        expect(queryByText("Bad expression")).toBeNull();

        // Popup closes — errors must NOT flash; waitingForFreshValidation still active
        closePopupViaKeyboard(rerender, { ...props, fieldErrors: errors });
        act(() => jest.advanceTimersByTime(100));
        expect(queryByText("Bad expression")).toBeNull();

        // Post-close validation cycle — errors now visible
        simulateValidationCycle(rerender, { ...props, fieldErrors: errors });
        act(() => jest.advanceTimersByTime(100));
        expect(queryByText("Bad expression")).not.toBeNull();
    });

    it("does not show validation errors while typing #'' with autocomplete popup open", () => {
        const onValueChange = jest.fn();
        const props = buildProps(onValueChange);
        const { queryByText, rerender } = render(<CustomCompleterAceEditor {...props} fieldErrors={[]} isValidating />);

        focusEditor();

        // User types "#" — autocomplete popup opens
        openPopup(rerender, { ...props, fieldErrors: [], isValidating: false });
        typeInEditor("#''");

        // Validation finishes with errors, but popup is still open — errors must stay hidden
        rerender(<CustomCompleterAceEditor {...props} fieldErrors={errors} isValidating={false} />);
        act(() => jest.advanceTimersByTime(100));
        expect(queryByText("Bad expression")).toBeNull();

        // User dismisses popup — errors still hidden until post-close validation cycle
        closePopupViaKeyboard(rerender, { ...props, fieldErrors: errors, isValidating: false });
        act(() => jest.advanceTimersByTime(100));
        expect(queryByText("Bad expression")).toBeNull();

        // Post-close validation cycle — errors reappear
        simulateValidationCycle(rerender, { ...props, fieldErrors: errors });
        act(() => jest.advanceTimersByTime(100));
        expect(queryByText("Bad expression")).not.toBeNull();
    });
});
