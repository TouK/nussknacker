import type React from "react";
import { useCallback, useState } from "react";

import { setTestCaseName } from "../../../../../../../actions/nk/testCasesActions";
import { useAppDispatch } from "../../../../../../../store/storeHelpers";
import { useTestCaseNameValidation } from "../../../../../../modals/useTestCaseNameValidation";

export const useNameEdit = (currentName: string | undefined) => {
    const dispatch = useAppDispatch();

    const [isEditing, setIsEditing] = useState(false);
    const [editValue, setEditValue] = useState("");

    const { nameErrors, isValid } = useTestCaseNameValidation(isEditing ? editValue : currentName ?? "", currentName);
    const editErrorMessage = isEditing ? nameErrors[0]?.message : undefined;

    const startEditing = useCallback(() => {
        setEditValue(currentName ?? "");
        setIsEditing(true);
    }, [currentName]);

    const confirmEdit = useCallback(() => {
        if (!isValid) return;
        const trimmed = editValue.trim();
        if (trimmed && trimmed !== currentName) {
            dispatch(setTestCaseName(trimmed));
        }
        setIsEditing(false);
    }, [dispatch, editValue, currentName, isValid]);

    const cancelEdit = useCallback(() => {
        setIsEditing(false);
    }, []);

    const handleBlur = useCallback(() => {
        if (isValid) {
            confirmEdit();
        } else {
            cancelEdit();
        }
    }, [isValid, cancelEdit, confirmEdit]);

    const handleKeyDown = useCallback(
        (e: React.KeyboardEvent) => {
            if (e.key === "Enter") {
                confirmEdit();
            } else if (e.key === "Escape") {
                e.stopPropagation();
                cancelEdit();
            }
        },
        [confirmEdit, cancelEdit],
    );

    return { isEditing, editValue, editErrorMessage, setEditValue, startEditing, handleBlur, handleKeyDown };
};
