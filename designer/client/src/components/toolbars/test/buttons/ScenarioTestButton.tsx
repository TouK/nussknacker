import { alpha, styled } from "@mui/material";
import React, { useCallback, useContext, useMemo } from "react";
import { useTranslation } from "react-i18next";

import { changeActiveTestCase } from "../../../../actions/nk/testingActions";
import TestingIcon from "../../../../assets/img/toolbarButtons/test.svg";
import { getActiveTestCase, getTestCases } from "../../../../reducers/selectors/testCases";
import { getActiveTestCaseAssertionResult, isGetActiveTestCaseAssertionResultLoading } from "../../../../reducers/selectors/testing";
import { ToolbarsSide } from "../../../../reducers/toolbars";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import { useTestingScenarioEnabled } from "../../../modals/TestingDataRecords/useTestingScenarioEnabled";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons/ToolbarButton";
import { ButtonsVariant, ToolbarButtonsContext } from "../../../toolbarComponents/toolbarButtons/ToolbarButtons";
import { ToolbarSideContext } from "../../../toolbarComponents/ToolbarsContainer";
import type { CustomButtonTypes } from "../../../toolbarSettings/buttons/buttonsMap";
import type { PropsOfButton } from "../../../toolbarSettings/buttons/types";
import { useRunTestScenario } from "../useRunTestScenario";
import { getAssertionResultsSummary } from "./scenarioTestButtonContent/getAssertionResultsSummary";
import { TestingIconWithAssertionStatus } from "./scenarioTestButtonContent/TestingIconWithAssertionStatus";
import type { Preset } from "./scenarioTestButtonContent/useScenarioTestPresets";
import { RUN_ALL, useScenarioTestPresets } from "./scenarioTestButtonContent/useScenarioTestPresets";
import { useScenarioTestTooltip } from "./scenarioTestButtonContent/useScenarioTestTooltip";

export type ScenarioTestButtonProps = {
    type: CustomButtonTypes.scenarioTest;
    title?: string;
    titleOverride?: string;
};

function ScenarioTestButton(props: PropsOfButton<CustomButtonTypes.scenarioTest>) {
    const { disabled, title, titleOverride, type } = props;
    const { t } = useTranslation();
    const testCase = useAppSelector(getActiveTestCase);
    const testCases = useAppSelector(getTestCases);
    const isLoading = useAppSelector(isGetActiveTestCaseAssertionResultLoading);
    const testingScenarioEnabled = useTestingScenarioEnabled({ disabled });

    const { variant } = useContext(ToolbarButtonsContext);
    const side = useContext(ToolbarSideContext);

    const { presets, activeTestCasePreset } = useScenarioTestPresets();

    const testCaseAssertionResult = useAppSelector(getActiveTestCaseAssertionResult);
    const { hasResult, assertionsIsSuccess } = getAssertionResultsSummary(testCaseAssertionResult);
    const tooltip = useScenarioTestTooltip({ disabled, title, titleOverride });

    const dispatch = useAppDispatch();

    const { runTest } = useRunTestScenario();

    const icon = useMemo(
        () =>
            activeTestCasePreset?.value === RUN_ALL ? (
                <TestingIcon />
            ) : (
                <TestingIconWithAssertionStatus hasResult={hasResult} assertionsIsSuccess={assertionsIsSuccess} />
            ),
        [activeTestCasePreset?.value, assertionsIsSuccess, hasResult],
    );

    const handleRunActiveTest = useCallback(() => runTest(testCase), [runTest, testCase]);

    const handleRunNextTestCase = useCallback(
        (testCaseId: string) => {
            const testCaseToRun = testCases.find((tc) => tc.id === testCaseId);
            if (!testCaseToRun) return;

            runTest(testCaseToRun);
        },
        [runTest, testCases],
    );

    const handlePresetChange = useCallback(
        (preset: Preset) => {
            if (preset.value === RUN_ALL) {
                //TODO: Implement me when backend ready
                return;
            }
            dispatch(changeActiveTestCase(preset.value));
            handleRunNextTestCase(preset.value);
        },
        [dispatch, handleRunNextTestCase],
    );

    return (
        <StyledScenarioTestButton
            onClick={handleRunActiveTest}
            name={activeTestCasePreset?.label}
            title={tooltip || t("panels.actions.scenarioTest.button.title", "run test")}
            icon={icon}
            side={side}
            variant={variant}
            isLoading={isLoading}
            disabled={!testingScenarioEnabled || isLoading}
            type={type}
            presets={presets}
            selected={activeTestCasePreset}
            onPresetChange={handlePresetChange}
        />
    );
}

export default ScenarioTestButton;

const StyledScenarioTestButton = styled(ToolbarButton, {
    shouldForwardProp: (prop) => prop !== "side" && prop !== "variant",
})<{ side: ToolbarsSide; variant: ButtonsVariant }>(({ theme, side, variant }) => {
    const normal = theme.palette.primary.main;
    const highlight = theme.palette.primary.light;
    const isHorizontal =
        variant === ButtonsVariant.xs && [ToolbarsSide.CenterTop, ToolbarsSide.CenterBottom, ToolbarsSide.AboveNodeWindow].includes(side);
    return {
        color: alpha(theme.palette.getContrastText(normal), 0.75),

        ".toolbarButton-Root": {
            backgroundColor: normal,
        },
        "&:hover": {
            color: theme.palette.getContrastText(highlight),
            ".toolbarButton-Root, .toolbarButton-MenuExpand": {
                backgroundColor: highlight,
            },
        },
        "button:focus-within": {
            color: theme.palette.getContrastText(highlight),
            outlineColor: theme.palette.background.paper,
            backgroundColor: highlight,
        },
        ".toolbarButton-Label": isHorizontal
            ? {
                  fontSize: "1em",
                  minWidth: "5em",
                  display: "inline",
                  textTransform: "initial",
              }
            : null,
    };
});
