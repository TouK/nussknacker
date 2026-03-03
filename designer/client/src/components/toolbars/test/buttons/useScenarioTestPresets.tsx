import { PlayArrow } from "@mui/icons-material";
import type { ReactNode } from "react";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";

import { getTestCase } from "../../../../reducers/selectors/testCases";
import { useAppSelector } from "../../../../store/storeHelpers";
import type { OptionHeader } from "../../../graph/node-modal/fragment-input-definition/TypeSelect";
import { AssertionStatusIcon } from "../../assertionResults/assertionResultsForNode/assertionResult/AssertionStatusIcon";
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
    const testCase = useAppSelector(getTestCase);
    const { hasResult, assertionsIsSuccess } = useAssertionResultsSummary();

    const testCasePresets: Preset[] = useMemo(() => {
        if (!testCase) return [];
        return [
            {
                icon: hasResult ? <AssertionStatusIcon isSuccess={assertionsIsSuccess} variant={"light"} /> : null,
                label: testCase.name,
                value: testCase.id,
            },
        ];
    }, [testCase, hasResult, assertionsIsSuccess]);

    const runAllPreset: Preset = useMemo(
        () => ({
            icon: <PlayArrow sx={{ fontSize: "20px" }} />,
            label: t("testingForm.test.menu.runAll", "Run all"),
            value: RUN_ALL,
        }),
        [t],
    );

    const presets: Array<Preset | OptionHeader> = useMemo(
        () =>
            testCasePresets.length > 1
                ? [runAllPreset, { header: "Test cases" }, ...testCasePresets]
                : [{ header: "Test cases" }, ...testCasePresets],
        [runAllPreset, testCasePresets],
    );

    return { presets, testCasePresets, runAllPreset };
};
