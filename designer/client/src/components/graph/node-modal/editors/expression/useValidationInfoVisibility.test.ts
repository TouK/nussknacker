import { act, renderHook } from "@testing-library/react";

import type { FieldError } from "../Validators";
import { useValidationInfoVisibility } from "./useValidationInfoVisibility";

jest.mock("rooks", () => ({
    usePreviousImmediate: jest.requireActual("rooks").usePreviousImmediate,
    usePreviousDifferent: jest.requireActual("rooks").usePreviousDifferent,
}));

type Props = Parameters<typeof useValidationInfoVisibility>[0];

// Errors without requestId — simulate contexts without node-modal Redux (e.g. data mapper)
const error: FieldError = { message: "Type error", description: "Expression is invalid" };

// Errors with requestId — simulate node-modal context where reducer injects a uuid per cycle
const errorV1: FieldError = { ...error, requestId: "req-1" };
const errorV2: FieldError = { ...error, requestId: "req-2" };

const defaultProps: Props = {
    fieldErrors: [],
    showValidation: true,
    completionsVisible: false,
    isLoading: false,
    validationLabelInfo: undefined,
    internalValue: "",
};

function makeProps(overrides: Partial<Props> = {}): Props {
    return { ...defaultProps, ...overrides };
}

describe("useValidationInfoVisibility", () => {
    describe("initial render", () => {
        it("shows errors immediately on mount", () => {
            const { result } = renderHook((props: Props) => useValidationInfoVisibility(props), {
                initialProps: makeProps({ fieldErrors: [error] }),
            });
            expect(result.current.visibleValidationErrors).toHaveLength(1);
        });
    });

    describe("isLoading=true", () => {
        it("suppresses snapshot updates while loading", () => {
            const { result, rerender } = renderHook((props: Props) => useValidationInfoVisibility(props), {
                initialProps: makeProps({ fieldErrors: [], isLoading: false }),
            });
            expect(result.current.visibleValidationErrors).toHaveLength(0);

            rerender(makeProps({ fieldErrors: [error], isLoading: true }));
            expect(result.current.visibleValidationErrors).toHaveLength(0);
        });

        it("shows errors once loading finishes", () => {
            const { result, rerender } = renderHook((props: Props) => useValidationInfoVisibility(props), {
                initialProps: makeProps({ fieldErrors: [], isLoading: false }),
            });

            rerender(makeProps({ fieldErrors: [error], isLoading: true }));
            expect(result.current.visibleValidationErrors).toHaveLength(0);

            rerender(makeProps({ fieldErrors: [error], isLoading: false }));
            expect(result.current.visibleValidationErrors).toHaveLength(1);
        });
    });

    describe("showValidation=false", () => {
        it("suppresses snapshot updates from normal flow when showValidation is false", () => {
            const { result, rerender } = renderHook((props: Props) => useValidationInfoVisibility(props), {
                initialProps: makeProps({ fieldErrors: [], showValidation: false }),
            });
            expect(result.current.visibleValidationErrors).toHaveLength(0);

            rerender(makeProps({ fieldErrors: [error], showValidation: false }));
            expect(result.current.visibleValidationErrors).toHaveLength(0);
        });
    });

    describe("popup open", () => {
        it("clears errors when completionsVisible becomes true", () => {
            const { result, rerender } = renderHook((props: Props) => useValidationInfoVisibility(props), {
                initialProps: makeProps({ fieldErrors: [error] }),
            });
            expect(result.current.visibleValidationErrors).toHaveLength(1);

            rerender(makeProps({ fieldErrors: [error], completionsVisible: true }));

            expect(result.current.visibleValidationErrors).toHaveLength(0);
        });

        it("keeps errors hidden while popup is open even when fieldErrors change", () => {
            const { result, rerender } = renderHook((props: Props) => useValidationInfoVisibility(props), {
                initialProps: makeProps({ fieldErrors: [error] }),
            });

            rerender(makeProps({ fieldErrors: [error], completionsVisible: true }));

            // Validation finishes with new errors while popup still open
            rerender(makeProps({ fieldErrors: [errorV1], completionsVisible: true }));
            expect(result.current.visibleValidationErrors).toHaveLength(0);
        });
    });

    describe("popup closed — waitingForNextRequest flow", () => {
        it("keeps errors hidden after popup closes until a new requestId arrives", () => {
            const { result, rerender } = renderHook((props: Props) => useValidationInfoVisibility(props), {
                initialProps: makeProps({ fieldErrors: [errorV1] }),
            });

            rerender(makeProps({ fieldErrors: [errorV1], completionsVisible: true }));
            expect(result.current.visibleValidationErrors).toHaveLength(0);

            rerender(makeProps({ fieldErrors: [errorV1], completionsVisible: false }));
            expect(result.current.visibleValidationErrors).toHaveLength(0); // still waiting

            // New validation cycle: same message, different requestId
            rerender(makeProps({ fieldErrors: [errorV2], completionsVisible: false }));
            expect(result.current.visibleValidationErrors).toHaveLength(1);
        });

        it("shows cleared errors (empty) when validation returns no errors after popup closes", () => {
            const { result, rerender } = renderHook((props: Props) => useValidationInfoVisibility(props), {
                initialProps: makeProps({ fieldErrors: [errorV1] }),
            });

            rerender(makeProps({ fieldErrors: [errorV1], completionsVisible: true }));
            rerender(makeProps({ fieldErrors: [errorV1], completionsVisible: false }));
            expect(result.current.visibleValidationErrors).toHaveLength(0);

            // Validation clears errors
            rerender(makeProps({ fieldErrors: [], completionsVisible: false }));
            expect(result.current.visibleValidationErrors).toHaveLength(0); // empty, but now correct
        });
    });

    describe("special case: internalValue === '#'", () => {
        it("force-restores snapshot when popup closes with internalValue='#' (no new requestId)", () => {
            const { result, rerender } = renderHook((props: Props) => useValidationInfoVisibility(props), {
                initialProps: makeProps({ fieldErrors: [error], internalValue: "#" }),
            });

            // Popup opens → snapshot cleared
            rerender(makeProps({ fieldErrors: [error], completionsVisible: true, internalValue: "#" }));
            expect(result.current.visibleValidationErrors).toHaveLength(0);

            // Popup closes with same value '#' and no new requestId — special case restores
            rerender(makeProps({ fieldErrors: [error], completionsVisible: false, internalValue: "#" }));
            expect(result.current.visibleValidationErrors).toHaveLength(1);
        });

        it("does not force-restore when internalValue is not '#'", () => {
            const { result, rerender } = renderHook((props: Props) => useValidationInfoVisibility(props), {
                initialProps: makeProps({ fieldErrors: [errorV1], internalValue: "someExpr" }),
            });

            rerender(makeProps({ fieldErrors: [errorV1], completionsVisible: true, internalValue: "someExpr" }));
            expect(result.current.visibleValidationErrors).toHaveLength(0);

            rerender(makeProps({ fieldErrors: [errorV1], completionsVisible: false, internalValue: "someExpr" }));
            // No special case, still waiting for next requestId
            expect(result.current.visibleValidationErrors).toHaveLength(0);
        });
    });

    describe("normal typing (no popup)", () => {
        it("always mirrors current fieldErrors when not loading and not waiting", () => {
            const { result, rerender } = renderHook((props: Props) => useValidationInfoVisibility(props), {
                initialProps: makeProps({ fieldErrors: [] }),
            });

            rerender(makeProps({ fieldErrors: [error] }));
            expect(result.current.visibleValidationErrors).toHaveLength(1);

            rerender(makeProps({ fieldErrors: [] }));
            expect(result.current.visibleValidationErrors).toHaveLength(0);
        });
    });

    describe("validationLabelInfo", () => {
        it("clears labelInfo when popup opens and restores it after the next validation cycle", () => {
            const info = "Validation info";

            const { result, rerender } = renderHook((props: Props) => useValidationInfoVisibility(props), {
                initialProps: makeProps({ fieldErrors: [errorV1], validationLabelInfo: info }),
            });
            expect(result.current.visibleValidationLabelInfo).toBe(info);

            rerender(makeProps({ fieldErrors: [errorV1], completionsVisible: true, validationLabelInfo: info }));
            expect(result.current.visibleValidationLabelInfo).toBeUndefined();

            // New validation cycle with new info
            rerender(makeProps({ fieldErrors: [errorV2], completionsVisible: false, validationLabelInfo: "New info" }));
            expect(result.current.visibleValidationLabelInfo).toBe("New info");
        });
    });
});
