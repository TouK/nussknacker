import { act, renderHook } from "@testing-library/react";

import type { FieldError } from "../Validators";
import { useValidationInfoVisibility } from "./useValidationInfoVisibility";

// Bypass debounce delays so state is observable immediately in assertions.
jest.mock("rooks", () => ({
    useDebouncedValue: (value: unknown) => [value],
    usePreviousImmediate: jest.requireActual("rooks").usePreviousImmediate,
}));

type Props = Parameters<typeof useValidationInfoVisibility>[0];

const error: FieldError = { message: "Type error", description: "Expression is invalid" };

const defaultProps: Props = {
    fieldErrors: [],
    showValidation: true,
    completionsVisible: false,
    isValidating: false,
    isLoading: false,
    validationLabelInfo: undefined,
};

function makeProps(overrides: Partial<Props> = {}): Props {
    return { ...defaultProps, ...overrides };
}

describe("useValidationInfoVisibility", () => {
    beforeEach(() => {
        jest.useFakeTimers();
    });

    afterEach(() => {
        jest.useRealTimers();
    });

    describe("without validation support (isValidating=undefined)", () => {
        it("should show errors immediately regardless of popup state", () => {
            const { result, rerender } = renderHook((props: Props) => useValidationInfoVisibility(props), {
                initialProps: makeProps({ isValidating: undefined, fieldErrors: [error] }),
            });

            expect(result.current.visibleValidationErrors).toHaveLength(1);

            // Popup open → close should have no effect
            rerender(makeProps({ isValidating: undefined, fieldErrors: [error], completionsVisible: true }));
            rerender(makeProps({ isValidating: undefined, fieldErrors: [error], completionsVisible: false }));

            expect(result.current.visibleValidationErrors).toHaveLength(1);
        });
    });

    describe("popup open", () => {
        it("should hide errors when popup opens", () => {
            const { result, rerender } = renderHook((props: Props) => useValidationInfoVisibility(props), {
                initialProps: makeProps({ fieldErrors: [error] }),
            });
            expect(result.current.visibleValidationErrors).toHaveLength(1);

            rerender(makeProps({ fieldErrors: [error], completionsVisible: true }));

            expect(result.current.visibleValidationErrors).toHaveLength(0);
        });
    });

    describe("popup closed, no validation running at close time", () => {
        it("should show errors after ~150 ms timeout when value is unchanged (no new validation cycle)", () => {
            const { result, rerender } = renderHook((props: Props) => useValidationInfoVisibility(props), {
                initialProps: makeProps({ fieldErrors: [error] }),
            });

            rerender(makeProps({ fieldErrors: [error], completionsVisible: true }));
            rerender(makeProps({ fieldErrors: [error], completionsVisible: false }));
            expect(result.current.visibleValidationErrors).toHaveLength(0); // watchingForValidationStart

            act(() => {
                jest.advanceTimersByTime(150);
            });
            expect(result.current.visibleValidationErrors).toHaveLength(1);
        });

        it("should wait for fresh result when validation starts before the 150 ms timeout (value changed)", () => {
            const { result, rerender } = renderHook((props: Props) => useValidationInfoVisibility(props), {
                initialProps: makeProps({ fieldErrors: [error] }),
            });

            rerender(makeProps({ fieldErrors: [error], completionsVisible: true }));
            rerender(makeProps({ fieldErrors: [error], completionsVisible: false }));

            // Parent starts re-validation within 150 ms
            rerender(makeProps({ fieldErrors: [error], completionsVisible: false, isValidating: true }));
            expect(result.current.visibleValidationErrors).toHaveLength(0); // waitingForFreshResult

            // Timeout fires but validation is in flight — timeout was cancelled, phase unchanged
            act(() => {
                jest.advanceTimersByTime(150);
            });
            expect(result.current.visibleValidationErrors).toHaveLength(0);

            // Validation finishes with fresh (empty) result
            rerender(makeProps({ fieldErrors: [], completionsVisible: false, isValidating: false }));
            expect(result.current.visibleValidationErrors).toHaveLength(0);
        });
    });

    describe("popup closed, validation in flight at close time", () => {
        it("should show errors once validation finishes after popup closes", () => {
            const { result, rerender } = renderHook((props: Props) => useValidationInfoVisibility(props), {
                initialProps: makeProps({ fieldErrors: [error] }),
            });

            // Open popup while validation is running
            rerender(makeProps({ fieldErrors: [error], completionsVisible: true, isValidating: true }));
            expect(result.current.visibleValidationErrors).toHaveLength(0);

            // Close popup — validation still running
            rerender(makeProps({ fieldErrors: [error], completionsVisible: false, isValidating: true }));
            expect(result.current.visibleValidationErrors).toHaveLength(0);

            // Validation finishes after popup close
            rerender(makeProps({ fieldErrors: [error], completionsVisible: false, isValidating: false }));
            expect(result.current.visibleValidationErrors).toHaveLength(1);
        });
    });

    describe("popup closed, validation completed while popup was open", () => {
        it("should show errors after ~150 ms timeout when value is unchanged after close", () => {
            // This was the regression: validatedWhileOpen guard kept phase in
            // waitingForFreshResult forever when no new validation cycle followed.
            const { result, rerender } = renderHook((props: Props) => useValidationInfoVisibility(props), {
                initialProps: makeProps({ fieldErrors: [error] }),
            });

            // Open popup
            rerender(makeProps({ fieldErrors: [error], completionsVisible: true }));

            // Validation runs and finishes while popup is open
            rerender(makeProps({ fieldErrors: [error], completionsVisible: true, isValidating: true }));
            rerender(makeProps({ fieldErrors: [error], completionsVisible: true, isValidating: false }));
            expect(result.current.visibleValidationErrors).toHaveLength(0);

            // Popup closes — no new validation (value unchanged)
            rerender(makeProps({ fieldErrors: [error], completionsVisible: false, isValidating: false }));
            expect(result.current.visibleValidationErrors).toHaveLength(0); // watchingForValidationStart

            act(() => {
                jest.advanceTimersByTime(150);
            });
            expect(result.current.visibleValidationErrors).toHaveLength(1);
        });

        it("should show fresh errors when value changed after close and new validation finishes", () => {
            const { result, rerender } = renderHook((props: Props) => useValidationInfoVisibility(props), {
                initialProps: makeProps({ fieldErrors: [error] }),
            });

            rerender(makeProps({ fieldErrors: [error], completionsVisible: true }));
            rerender(makeProps({ fieldErrors: [error], completionsVisible: true, isValidating: true }));
            rerender(makeProps({ fieldErrors: [error], completionsVisible: true, isValidating: false }));

            // Popup closes — value changed → new validation starts before timeout
            rerender(makeProps({ fieldErrors: [error], completionsVisible: false, isValidating: false }));
            rerender(makeProps({ fieldErrors: [error], completionsVisible: false, isValidating: true }));
            expect(result.current.visibleValidationErrors).toHaveLength(0); // waitingForFreshResult

            // New validation finishes with no errors
            rerender(makeProps({ fieldErrors: [], completionsVisible: false, isValidating: false }));
            expect(result.current.visibleValidationErrors).toHaveLength(0);
        });
    });

    describe("re-validation while typing (no popup)", () => {
        it("should keep errors visible while re-validating", () => {
            const { result, rerender } = renderHook((props: Props) => useValidationInfoVisibility(props), {
                initialProps: makeProps({ fieldErrors: [error] }),
            });
            expect(result.current.visibleValidationErrors).toHaveLength(1);

            rerender(makeProps({ fieldErrors: [error], isValidating: true }));
            expect(result.current.visibleValidationErrors).toHaveLength(1);

            rerender(makeProps({ fieldErrors: [error], isValidating: false }));
            expect(result.current.visibleValidationErrors).toHaveLength(1);
        });

        it("should reflect updated errors once re-validation finishes", () => {
            const { result, rerender } = renderHook((props: Props) => useValidationInfoVisibility(props), {
                initialProps: makeProps({ fieldErrors: [error] }),
            });

            rerender(makeProps({ fieldErrors: [error], isValidating: true }));
            rerender(makeProps({ fieldErrors: [], isValidating: false }));

            expect(result.current.visibleValidationErrors).toHaveLength(0);
        });
    });

    describe("showValidation=false", () => {
        it("should always return empty errors regardless of phase", () => {
            const { result, rerender } = renderHook((props: Props) => useValidationInfoVisibility(props), {
                initialProps: makeProps({ fieldErrors: [error], showValidation: false }),
            });
            expect(result.current.visibleValidationErrors).toHaveLength(0);

            rerender(makeProps({ fieldErrors: [error], showValidation: false, completionsVisible: true }));
            rerender(makeProps({ fieldErrors: [error], showValidation: false, completionsVisible: false }));
            act(() => {
                jest.advanceTimersByTime(150);
            });

            expect(result.current.visibleValidationErrors).toHaveLength(0);
        });
    });
});
