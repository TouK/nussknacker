import { alpha, styled } from "@mui/material";
import React, { useCallback, useContext, useState } from "react";
import { useTranslation } from "react-i18next";

import { testScenarioWithTestCase } from "../../../../actions/nk/testingActions";
import TestingIcon from "../../../../assets/img/toolbarButtons/test.svg";
import { useUserSettings } from "../../../../common/useUserSettings";
import type { TestCase } from "../../../../reducers/graph/testCase";
import { getTestCase } from "../../../../reducers/selectors/testCases";
import { getTestResultsLoading } from "../../../../reducers/selectors/testing";
import { ToolbarsSide } from "../../../../reducers/toolbars";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import { useTestingScenarioEnabled } from "../../../modals/TestingDataRecords/useTestingScenarioEnabled";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons/ToolbarButton";
import { ButtonsVariant, ToolbarButtonsContext } from "../../../toolbarComponents/toolbarButtons/ToolbarButtons";
import { ToolbarSideContext } from "../../../toolbarComponents/ToolbarsContainer";
import type { CustomButtonTypes } from "../../../toolbarSettings/buttons/buttonsMap";
import type { PropsOfButton } from "../../../toolbarSettings/buttons/types";
import { TestingIconWithAssertionStatus } from "./TestingIconWithAssertionStatus";
import { useAssertionResultsSummary } from "./useAssertionResultsSummary";
import { RUN_ALL, useScenarioTestPresets } from "./useScenarioTestPresets";
import { useScenarioTestTooltip } from "./useScenarioTestTooltip";

export type ScenarioTestButtonProps = {
    type: CustomButtonTypes.scenarioTest;
    title?: string;
    titleOverride?: string;
};

function ScenarioTestButton(props: PropsOfButton<CustomButtonTypes.scenarioTest>) {
    const { disabled, title, titleOverride, type } = props;
    const { t } = useTranslation();
    const testCase = useAppSelector(getTestCase);
    const isLoading = useAppSelector(getTestResultsLoading);
    const testingScenarioEnabled = useTestingScenarioEnabled({ disabled });

    const { variant } = useContext(ToolbarButtonsContext);
    const side = useContext(ToolbarSideContext);

    const { presets, runAllPreset, testCasePresets } = useScenarioTestPresets();
    const { hasResult, assertionsIsSuccess } = useAssertionResultsSummary();
    const tooltip = useScenarioTestTooltip({ disabled, title, titleOverride });

    const [selectedPreset, setSelectedPreset] = useState(testCasePresets[0] || runAllPreset);

    const [showMockFieldOnEnrichers] = useUserSettings("node.showMockFieldOnEnrichers");
    const dispatch = useAppDispatch();
    const handleRunTest = useCallback(
        (testCase: TestCase) => dispatch(testScenarioWithTestCase(testCase, showMockFieldOnEnrichers)),
        [dispatch, showMockFieldOnEnrichers],
    );

    const icon =
        selectedPreset.value === RUN_ALL ? (
            <TestingIcon />
        ) : (
            <TestingIconWithAssertionStatus hasResult={hasResult} assertionsIsSuccess={assertionsIsSuccess} />
        );

    return (
        <StyledScenarioTestButton
            onClick={() => handleRunTest(testCase)}
            name={selectedPreset.label}
            title={tooltip || t("panels.actions.scenarioTest.button.title", "run test")}
            icon={icon}
            side={side}
            variant={variant}
            isLoading={isLoading}
            disabled={!testingScenarioEnabled || isLoading}
            type={type}
            presets={presets}
            selected={selectedPreset}
            onPresetChange={(preset) => {
                setSelectedPreset(preset);
                if (preset.value === RUN_ALL) {
                    //TODO: Implement me when backend ready
                    return;
                }
                //TODO: Handle multiple test selection when backend ready
                handleRunTest(testCase);
            }}
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
