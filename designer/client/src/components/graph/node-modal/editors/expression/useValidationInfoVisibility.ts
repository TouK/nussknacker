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
 *    `waitingForFreshValidation` is set to true and remains set until a full
 *    validation cycle completes after the popup closes (see use case 3).
 *
 * 3. Autocomplete popup closes, validation in flight
 *    `waitingForFreshValidation` keeps errors hidden until `isValidating`
 *    transitions true→false *while the popup is closed*.
 *    Guards against showing stale errors between "popup closed" and
 *    "new validation response arrived".
 *
 * 4. Autocomplete popup closes, validation already completed while popup was open
 *    Without the `!completionsVisible` guard the transition would be detected
 *    while the popup is still open, clearing suppression too early. The guard
 *    ensures suppression is only lifted after popup is gone and the next
 *    validation cycle (triggered by onValueChange) finishes.
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
    const [debouncedLoading] = useDebouncedValue(isLoading, 100);

    const [snapshot, setSnapshot] = useState({
        errors: fieldErrors,
        info: validationLabelInfo,
    });

    const [state, dispatch] = useReducer(reducer, { waiting: false });

    const prevIsValidating = usePreviousImmediate(isValidating);

    useEffect(() => {
        if (!debouncedLoading && !completionsVisible) {
            setSnapshot({
                errors: fieldErrors,
                info: validationLabelInfo,
            });
        }
    }, [debouncedLoading, completionsVisible, fieldErrors, validationLabelInfo]);

    useEffect(() => {
        if (completionsVisible) {
            dispatch({
                type: "POPUP_OPEN",
                hasValidation: typeof isValidating !== "undefined",
            });
        }
    }, [completionsVisible, isValidating]);

    useEffect(() => {
        if (typeof isValidating === "undefined") return;

        if (prevIsValidating && !isValidating) {
            dispatch({
                type: "VALIDATION_FINISHED",
                completionsVisible,
            });
        }
    }, [prevIsValidating, isValidating, completionsVisible]);

    const waitingForFreshValidation = state.waiting;

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

type State = {
    waiting: boolean;
};

type Action =
    | { type: "POPUP_OPEN"; hasValidation: boolean }
    | { type: "VALIDATION_FINISHED"; completionsVisible: boolean }
    | { type: "RESET" };

function reducer(state: State, action: Action): State {
    switch (action.type) {
        case "POPUP_OPEN":
            if (!action.hasValidation) return state;
            return { waiting: true };

        case "VALIDATION_FINISHED":
            if (action.completionsVisible) return state;
            return { waiting: false };

        default:
            return state;
    }
}
