import { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";

import { removeTestCase } from "../../../../../../actions/nk/testCasesActions";
import { useAppDispatch } from "../../../../../../store/storeHelpers";

export const useTestCaseDelete = (activeTestCaseId: string | undefined, testCasesCount: number, isEditing: boolean) => {
    const { t } = useTranslation();
    const dispatch = useAppDispatch();

    const [isConfirming, setIsConfirming] = useState(false);

    const isOnlyOne = testCasesCount <= 1;
    const isDisabled = isOnlyOne || isEditing;

    const disabledTooltip = isEditing
        ? t("testCaseDelete.disabledEditing", "Finish editing before deleting")
        : isOnlyOne
        ? t("testCaseDelete.disabledOnlyOne", "Cannot delete the only test case")
        : undefined;

    const startDeleting = useCallback(() => setIsConfirming(true), []);

    const cancelDelete = useCallback(() => setIsConfirming(false), []);

    const confirmDelete = useCallback(() => {
        if (!activeTestCaseId) return;
        dispatch(removeTestCase(activeTestCaseId));
        setIsConfirming(false);
    }, [dispatch, activeTestCaseId]);

    return { isConfirming, isDisabled, disabledTooltip, startDeleting, cancelDelete, confirmDelete };
};
