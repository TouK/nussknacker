import { PlayArrow } from "@mui/icons-material";
import { alpha, styled, Box } from "@mui/material";
import type { ReactNode } from "react";
import React, { useCallback, useContext, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { testScenarioWithTestCase } from "../../../../actions/nk/testingActions";
import TestingIcon from "../../../../assets/img/toolbarButtons/test.svg";
import { useUserSettings } from "../../../../common/useUserSettings";
import { calculateAssertionResultsSummary } from "../../../../containers/assertions/assertionResultsUtils";
import type { TestCase } from "../../../../reducers/graph/testCase";
import { getTestCase } from "../../../../reducers/selectors/testCases";
import { getTestAssertionResults, getTestResultsLoading } from "../../../../reducers/selectors/testing";
import { ToolbarsSide } from "../../../../reducers/toolbars";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import type { OptionHeader } from "../../../graph/node-modal/fragment-input-definition/TypeSelect";
import { useTestingScenarioEnabled } from "../../../modals/TestingDataRecords/useTestingScenarioEnabled";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons/ToolbarButton";
import { ButtonsVariant, ToolbarButtonsContext } from "../../../toolbarComponents/toolbarButtons/ToolbarButtons";
import { ToolbarSideContext } from "../../../toolbarComponents/ToolbarsContainer";
import type { CustomButtonTypes } from "../../../toolbarSettings/buttons/buttonsMap";
import type { PropsOfButton } from "../../../toolbarSettings/buttons/types";
import { AssertionStatusIcon } from "../../assertionResults/assertionResult/AssertionStatusIcon";

export type ScenarioTestButtonProps = {
    type: CustomButtonTypes.scenarioTest;
    name?: string;
    title?: string;
    titleOverride?: string;
};

type Preset = {
    value: string;
    label: string;
    isDisabled?: boolean;
    icon?: ReactNode;
};

const RUN_ALL = "runAll";

function ScenarioTestButton(props: PropsOfButton<CustomButtonTypes.scenarioTest>) {
    const { disabled, title, titleOverride, type } = props;
    const { t } = useTranslation();
    const testCase = useAppSelector(getTestCase);
    const testAssertionResults = useAppSelector(getTestAssertionResults);
    const allResults = Object.values(testAssertionResults).flat();
    const { hasResult, failedCount } = calculateAssertionResultsSummary(allResults);
    const assertionsIsSuccess = hasResult && failedCount === 0;
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

    const [selectedPreset, setSelectedPreset] = useState<Preset | null>(testCasePresets[0] || runAllPreset);

    const presets: Array<Preset | OptionHeader> = useMemo(() => {
        return [runAllPreset, { header: "Test cases" }, ...testCasePresets];
    }, [runAllPreset, testCasePresets]);

    const isLoading = useAppSelector(getTestResultsLoading);

    const testingScenarioEnabled = useTestingScenarioEnabled({ disabled });

    const { variant } = useContext(ToolbarButtonsContext);
    const side = useContext(ToolbarSideContext);

    const tooltip: string =
        titleOverride ??
        (disabled
            ? t(
                  "panels.actions.scenarioTest.button.testing-not-available-in-current-state-title",
                  "Scenario testing is not supported for scenario in current state",
              )
            : !testingScenarioEnabled
            ? t(
                  "panels.actions.scenarioTest.button.testing-not-available-for-current-sources-title",
                  "Scenario testing is not supported for currently configured sources",
              )
            : title);

    const [showMockFieldOnEnrichers] = useUserSettings("node.showMockFieldOnEnrichers");
    const dispatch = useAppDispatch();
    const handleRunTest = useCallback(
        (testCase: TestCase) => {
            return dispatch(testScenarioWithTestCase(testCase, showMockFieldOnEnrichers));
        },
        [dispatch, showMockFieldOnEnrichers],
    );

    return (
        <StyledScenarioTestButton
            onClick={() => {
                handleRunTest(testCase);
            }}
            name={selectedPreset.label}
            title={tooltip || t("panels.actions.scenarioTest.button.title", "run test")}
            icon={
                selectedPreset.value === RUN_ALL ? (
                    <TestingIcon />
                ) : (
                    <Box
                        component="span"
                        sx={{
                            position: "relative",
                            display: "inline-flex",
                            alignItems: "center",
                            justifyContent: "center",
                        }}
                    >
                        <TestingIcon />
                        {hasResult ? (
                            <Box
                                component="span"
                                sx={{
                                    position: "absolute",
                                    top: 0,
                                    right: 0,
                                    transform: "translate(5%,0)",
                                    display: "inline-flex",
                                    alignItems: "center",
                                    justifyContent: "center",
                                    pointerEvents: "none",
                                    "& > svg": { fontSize: "16px" },
                                }}
                            >
                                <AssertionStatusIcon isSuccess={assertionsIsSuccess} variant={"dark"} />
                            </Box>
                        ) : null}
                    </Box>
                )
            }
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

const StyledScenarioTestButton = styled(ToolbarButton)<{ side: ToolbarsSide; variant: ButtonsVariant }>(({ theme, side, variant }) => {
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
