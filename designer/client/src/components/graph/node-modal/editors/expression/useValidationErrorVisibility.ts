import { isEmpty } from "lodash";
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
export function useValidationErrorVisibility({
    fieldErrors,
    showValidation,
    completionsVisible,
    isValidating,
}: {
    fieldErrors?: FieldError[];
    showValidation?: boolean;
    completionsVisible: boolean;
    isValidating?: boolean;
}): boolean {
    const [waitingForFreshValidation, setWaitingForFreshValidation] = useState(false);

    useEffect(() => {
        if (completionsVisible) {
            setWaitingForFreshValidation(true);
        }
    }, [completionsVisible]);

    const prevIsValidating = usePreviousImmediate(isValidating);
    useEffect(() => {
        if (prevIsValidating && !isValidating && !completionsVisible) {
            setWaitingForFreshValidation(false);
        }
    }, [completionsVisible, isValidating, prevIsValidating]);

    const validationErrorVisible = useMemo(
        () => showValidation && !isEmpty(fieldErrors) && !completionsVisible && !waitingForFreshValidation,
        [completionsVisible, fieldErrors, showValidation, waitingForFreshValidation],
    );

    const [debouncedVisible] = useDebouncedValue(validationErrorVisible, 100);

    return debouncedVisible ?? false;
}
