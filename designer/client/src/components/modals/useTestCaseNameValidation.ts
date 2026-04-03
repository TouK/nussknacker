import { useTranslation } from "react-i18next";

import { getTestCases } from "../../reducers/selectors/testCases";
import { useAppSelector } from "../../store/storeHelpers";

export const MAX_TEST_CASE_NAME_LENGTH = 100;

/**
 * @param name - the name being validated
 * @param excludeName - for rename: pass the current name to exclude it from duplicate check
 */
export const useTestCaseNameValidation = (name: string, excludeName?: string) => {
    const { t } = useTranslation();
    const testCases = useAppSelector(getTestCases);

    const nameErrors = [
        name.trim().length === 0 && t("testCaseName.error.nameEmpty", "Name cannot be empty"),
        name.trim().length > MAX_TEST_CASE_NAME_LENGTH &&
            t("testCaseName.error.nameTooLong", "Name cannot exceed {{max}} characters", { max: MAX_TEST_CASE_NAME_LENGTH }),
        testCases.some((tc) => tc.name === name.trim() && tc.name !== excludeName) &&
            t("testCaseName.error.nameExists", "Test case with this name already exists"),
    ]
        .filter((e): e is string => Boolean(e))
        .map((message) => ({ message, description: "" }));

    return { nameErrors, isValid: nameErrors.length === 0 };
};
