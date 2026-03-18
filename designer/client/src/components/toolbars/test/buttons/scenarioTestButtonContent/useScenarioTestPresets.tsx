import { PlayArrow } from "@mui/icons-material";
import type { ReactNode } from "react";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";

import { getTestCases } from "../../../../../reducers/selectors/testCases";
import { getActiveTestCaseId } from "../../../../../reducers/selectors/testing";
import { useAppSelector } from "../../../../../store/storeHelpers";
import type { OptionHeader } from "../../../../graph/node-modal/fragment-input-definition/TypeSelect";
import { AssertionStatusIcon } from "../../../testCases/assertionResultsForNode/assertionResult/AssertionStatusIcon";
import { useAssertionResultsSummary } from "./useAssertionResultsSummary";

export const RUN_ALL = "runAll";

export type Preset = {
    value: string;
    label: string;
    isDisabled?: boolean;
    icon?: ReactNode;
};

export const useScenarioTestPresets = () => {
    const { t } = useTranslation();
    const testCases = useAppSelector(getTestCases);
    const activeTestCaseId = useAppSelector(getActiveTestCaseId);
    const { hasResult, assertionsIsSuccess } = useAssertionResultsSummary();

    const testCasePresets: Preset[] = useMemo(() => {
        if (testCases.length === 0) return [];
        return testCases.map((testCase) => ({
            icon:
                hasResult && activeTestCaseId === testCase.id ? (
                    <AssertionStatusIcon isSuccess={assertionsIsSuccess} variant={"light"} />
                ) : null,
            label: testCase.name,
            value: testCase.id,
        }));
    }, [testCases, hasResult, activeTestCaseId, assertionsIsSuccess]);

    const activeTestCasePreset = testCasePresets.find((testCasePreset) => testCasePreset.value === activeTestCaseId) || null;

    const runAllPreset: Preset = useMemo(
        () => ({
            icon: <PlayArrow sx={{ fontSize: "20px" }} />,
            label: t("testingForm.test.menu.runAll", "Run all"),
            value: RUN_ALL,
        }),
        [t],
    );

    const testCasesHeader = useMemo(() => ({ header: t("testingForm.test.menu.testCasesHeader", "Test cases") }), [t]);

    const presets: Array<Preset | OptionHeader> = useMemo(
        () => (testCasePresets.length > 1 ? [runAllPreset, testCasesHeader, ...testCasePresets] : [testCasesHeader, ...testCasePresets]),
        [runAllPreset, testCasePresets, testCasesHeader],
    );

    return { presets, testCasePresets, runAllPreset, activeTestCasePreset };
};
