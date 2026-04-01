import type { ReactNode } from "react";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";

import TestingIcon from "../../../../../assets/img/toolbarButtons/test.svg";
import { getTestCases } from "../../../../../reducers/selectors/testCases";
import { getActiveTestCaseId, getTestAssertionResults } from "../../../../../reducers/selectors/testing";
import { useAppSelector } from "../../../../../store/storeHelpers";
import type { OptionHeader } from "../../../../graph/node-modal/fragment-input-definition/TypeSelect";
import { AssertionStatusIcon } from "../../../testCases/assertionResultsForNode/assertionResult/AssertionStatusIcon";
import { getAssertionResultsSummary } from "./getAssertionResultsSummary";

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
    const testAssertionResults = useAppSelector(getTestAssertionResults);

    const testCasePresets: Preset[] = useMemo(() => {
        if (testCases.length === 0) return [];
        return testCases.map((testCase) => {
            const { status } = getAssertionResultsSummary(testAssertionResults[testCase.id]);

            return {
                icon: status ? <AssertionStatusIcon status={status} variant={"light"} /> : null,
                label: testCase.name,
                value: testCase.id,
            };
        });
    }, [testCases, testAssertionResults]);

    const activeTestCaseId = useAppSelector(getActiveTestCaseId);
    const activeTestCasePreset = testCasePresets.find((testCasePreset) => testCasePreset.value === activeTestCaseId) || null;

    const runAllPreset: Preset = useMemo(
        () => ({
            icon: <TestingIcon style={{ color: "white", marginRight: "6px" }} />,
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
