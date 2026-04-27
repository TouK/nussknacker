import type { ReactNode } from "react";
import { useEffect, useMemo, useState } from "react";
import { usePreviousDifferent, usePreviousImmediate } from "rooks";

import type { FieldError } from "../Validators";

/**
 * Controls when validation errors are visible in an ACE expression editor.
 *
 * Uses a snapshot of (errors, labelInfo) and a `waitingForNextRequest` flag to
 * prevent stale errors from flashing after the autocomplete popup closes.
 *
 * Rules:
 *
 * 1. isLoading = true
 *    All snapshot updates are suppressed.
 *
 * 2. Popup opens (completionsVisible = true)
 *    Snapshot is cleared and waitingForNextRequest is set. Errors stay hidden
 *    until a new validation result arrives after the popup closes.
 *
 * 3. Not waiting (waitingForNextRequest = false, no popup open)
 *    Snapshot always mirrors the current fieldErrors. This is the default state
 *    during normal typing (no popup) and after the popup flow completes.
 *
 * 4. Popup closed + new requestId (fieldErrors carry a fresh requestId from the store)
 *    waitingForNextRequest is cleared so rule 3 takes effect on the next render.
 *
 * 5. Special case: internalValue === "#" when popup closes
 *    Snapshot is force-restored from current fieldErrors even if no new validation
 *    cycle arrived. Handles the SpEL `#` trigger: the popup opens before validation
 *    fires, and if the user dismisses it without typing, no new requestId ever arrives.
 *
 * 6. showValidation = false
 *    Snapshot updates are suppressed.
 */
export function useValidationInfoVisibility({
    fieldErrors,
    showValidation,
    completionsVisible,
    isLoading,
    validationLabelInfo,
    internalValue,
}: {
    fieldErrors: FieldError[];
    showValidation: boolean;
    completionsVisible: boolean;
    isLoading: boolean;
    validationLabelInfo?: ReactNode;
    internalValue: string;
}): { visibleValidationErrors: FieldError[]; visibleValidationLabelInfo: ReactNode } {
    const [snapshot, setSnapshot] = useState({
        errors: fieldErrors,
        info: validationLabelInfo,
    });
    const [waitingForNextRequest, setWaitingForNextRequest] = useState(false);

    const currId = useMemo(() => fieldErrors[0]?.requestId, [fieldErrors]);
    const prevId = usePreviousImmediate(currId);

    const prevCompletionVisible = usePreviousDifferent(completionsVisible);
    const popupClosed = prevCompletionVisible && !completionsVisible;

    useEffect(() => {
        if (!showValidation) {
            return;
        }

        // Special case, when validation is not fired, but we want to show error on completion close
        if (internalValue === "#" && popupClosed) {
            setSnapshot({ errors: fieldErrors, info: validationLabelInfo });
        }
    }, [fieldErrors, internalValue, popupClosed, showValidation, validationLabelInfo]);

    useEffect(() => {
        if (!showValidation) {
            return;
        }

        const nextRequest = prevId !== currId;
        if (nextRequest && popupClosed) {
            setWaitingForNextRequest(false);
        }
    }, [currId, popupClosed, prevId, showValidation, waitingForNextRequest]);

    useEffect(() => {
        if (!showValidation || isLoading) {
            return;
        }

        if (completionsVisible) {
            setSnapshot({ errors: [], info: undefined });
            setWaitingForNextRequest(true);
        } else if (!waitingForNextRequest) {
            setSnapshot({ errors: fieldErrors, info: validationLabelInfo });
        }
    }, [completionsVisible, fieldErrors, isLoading, showValidation, validationLabelInfo, waitingForNextRequest, setWaitingForNextRequest]);

    return {
        visibleValidationErrors: snapshot.errors,
        visibleValidationLabelInfo: snapshot.info,
    };
}
