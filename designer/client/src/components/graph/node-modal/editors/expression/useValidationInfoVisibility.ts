import { isEmpty } from "lodash";
import type { ReactNode } from "react";
import { useReducer } from "react";
import { useEffect, useMemo, useState } from "react";
import { useDebouncedValue, usePreviousImmediate } from "rooks";

import type { FieldError } from "../Validators";

/**
 * Controls when validation errors are visible in an ACE expression editor.
 *
 * Use cases:
 *
 * 1. Re-validation while typing (no autocomplete popup)
 *    Errors stay visible while a new validation request is in flight.
 *    The old error remains on screen until the fresh result replaces it — no blank flash.
 *
 * 2. Autocomplete popup opens
 *    Errors are hidden immediately so they do not overlap the suggestion list.
 *    Phase moves to `waitingForFreshResult` and stays there until a full validation
 *    cycle completes after the popup closes.
 *
 * 3. Autocomplete popup closes, validation in flight at close time
 *    Errors stay hidden until `isValidating` transitions true→false while popup is closed.
 *    Guards against showing stale errors between "popup closed" and "new result arrived".
 *
 * 4. Autocomplete popup closes, no validation running at close time
 *    Phase moves to `watchingForValidationStart`.
 *    — If `isValidating` goes true within ~150 ms (value changed → parent re-validates):
 *      phase moves back to `waitingForFreshResult` and resolves via use case 3.
 *    — If nothing starts within ~150 ms (value unchanged → no new cycle):
 *      phase moves to `idle` so the still-valid pre-popup errors are shown.
 *    This prevents both the flash (clearing too early) and getting stuck
 *    (clearing never happening when value is unchanged).
 *
 * 5. Visibility transitions are debounced (100 ms)
 *    Prevents a single-frame flicker when multiple state updates land in the
 *    same render batch (e.g. completionsVisible + fieldErrors change together).
 */
export function useValidationInfoVisibility({
    fieldErrors,
    showValidation,
    completionsVisible,
    isValidating,
    isLoading,
    validationLabelInfo,
}: {
    fieldErrors?: FieldError[];
    showValidation?: boolean;
    completionsVisible: boolean;
    isValidating?: boolean;
    isLoading?: boolean;
    validationLabelInfo?: ReactNode;
}): { visibleValidationErrors: FieldError[]; visibleValidationLabelInfo: ReactNode } {
    const [debouncedLoading] = useDebouncedValue(isLoading, 200);
    const [snapshot, setSnapshot] = useState({
        errors: fieldErrors,
        info: validationLabelInfo,
    });

    const [state, dispatch] = useReducer(reducer, { phase: "idle" });

    const prevIsValidating = usePreviousImmediate(isValidating);
    const prevIsCompletionsVisible = usePreviousImmediate(completionsVisible);

    useEffect(() => {
        if (!debouncedLoading && !completionsVisible) {
            setSnapshot({
                errors: fieldErrors,
                info: validationLabelInfo,
            });
        }
    }, [debouncedLoading, completionsVisible, fieldErrors, validationLabelInfo]);

    const hasValidation = isValidating !== undefined;
    useEffect(() => {
        if (completionsVisible) {
            dispatch({ type: "POPUP_OPEN", hasValidation });
        }
    }, [completionsVisible, hasValidation]);

    useEffect(() => {
        if (isValidating === undefined) return;

        if (prevIsValidating && !isValidating) {
            dispatch({ type: "VALIDATION_FINISHED", completionsVisible });
        } else if (prevIsCompletionsVisible && !completionsVisible && !prevIsValidating && !isValidating) {
            dispatch({ type: "POPUP_CLOSED_NO_VALIDATION" });
        }
    }, [prevIsValidating, isValidating, completionsVisible, prevIsCompletionsVisible]);

    // When watching for a new validation cycle to start: if isValidating goes true,
    // transition back to waitingForFreshResult and let VALIDATION_FINISHED resolve it.
    useEffect(() => {
        if (state.phase !== "watchingForValidationStart" || !isValidating) return;
        dispatch({ type: "VALIDATION_STARTED_AFTER_POPUP_CLOSE" });
    }, [state.phase, isValidating]);

    // Fallback: if no validation starts within ~150 ms (value unchanged),
    // clear waiting so the still-valid pre-popup errors are shown.
    useEffect(() => {
        if (state.phase !== "watchingForValidationStart") return;
        const timer = setTimeout(() => {
            dispatch({ type: "TIMEOUT_NO_VALIDATION_AFTER_POPUP_CLOSE" });
        }, 150);
        return () => clearTimeout(timer);
    }, [state.phase]);

    const waitingForFreshValidation = state.phase !== "idle";

    const validationState = useMemo(() => {
        if (!showValidation || waitingForFreshValidation) {
            return "hidden";
        }

        if (!isEmpty(snapshot.errors)) {
            return "error";
        }

        if (snapshot.info && !isValidating) {
            return "info";
        }

        return "none";
    }, [showValidation, waitingForFreshValidation, snapshot.errors, snapshot.info, isValidating]);

    const [debouncedValidationState] = useDebouncedValue(validationState, 100);

    return {
        visibleValidationErrors: debouncedValidationState === "error" ? snapshot.errors ?? [] : [],
        visibleValidationLabelInfo: debouncedValidationState === "info" ? snapshot.info ?? null : null,
    };
}

type Phase = "idle" | "waitingForFreshResult" | "watchingForValidationStart";

type State = {
    phase: Phase;
};

type Action =
    | { type: "POPUP_OPEN"; hasValidation: boolean }
    | { type: "VALIDATION_FINISHED"; completionsVisible: boolean }
    | { type: "POPUP_CLOSED_NO_VALIDATION" }
    | { type: "VALIDATION_STARTED_AFTER_POPUP_CLOSE" }
    | { type: "TIMEOUT_NO_VALIDATION_AFTER_POPUP_CLOSE" };

function reducer(state: State, action: Action): State {
    switch (action.type) {
        case "POPUP_OPEN":
            if (!action.hasValidation) return state;
            return { phase: "waitingForFreshResult" };

        case "VALIDATION_FINISHED":
            if (action.completionsVisible) return state;
            return { phase: "idle" };

        case "POPUP_CLOSED_NO_VALIDATION":
            if (state.phase !== "waitingForFreshResult") return state;
            return { phase: "watchingForValidationStart" };

        case "VALIDATION_STARTED_AFTER_POPUP_CLOSE":
            if (state.phase !== "watchingForValidationStart") return state;
            return { phase: "waitingForFreshResult" };

        case "TIMEOUT_NO_VALIDATION_AFTER_POPUP_CLOSE":
            if (state.phase !== "watchingForValidationStart") return state;
            return { phase: "idle" };

        default:
            return state;
    }
}
