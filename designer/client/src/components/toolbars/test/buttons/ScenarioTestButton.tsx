import loadable from "@loadable/component";
import React, { useCallback, useContext, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";

import { getTestCapabilities, getTestResultsLoading, getTestType, isLatestProcessVersion } from "../../../../reducers/selectors/graph";
import { ToolbarsSide } from "../../../../reducers/toolbars";
import { useWindows, WindowKind } from "../../../../windowManager";
import { useAdhocTestingAvailability } from "../../../modals/AdhocTesting/useAdhocTestingAvailability";
import { useTestingState } from "../../../modals/Testing/TestingContext";
import type { TestingData, TestingViewParams } from "../../../modals/Testing/TestingDialog";
import { TestType } from "../../../modals/Testing/TestingForm";
import { ButtonsVariant, ToolbarButton, ToolbarButtonsContext } from "../../../toolbarComponents/toolbarButtons";
import { ToolbarSideContext } from "../../../toolbarComponents/ToolbarsContainer";
import type { CustomButtonTypes, PropsOfButton } from "../../../toolbarSettings/buttons";
import type { Preset } from "../../types";

export type ScenarioTestButtonProps = {
    type: CustomButtonTypes.scenarioTest;
    name?: string;
    title?: string;
    docs?: TestingViewParams["docs"];
    markdownContent?: TestingViewParams["markdownContent"];
};

const TestingIcon = loadable(() => import("../../../../assets/img/toolbarButtons/test.svg"));

function ScenarioTestButton({ disabled, name, title, docs, markdownContent, type }: PropsOfButton<CustomButtonTypes.scenarioTest>) {
    const { t } = useTranslation();
    const { open } = useWindows();

    const testingState = useTestingState();
    const presets: Preset[] = useMemo(
        () => [
            {
                value: TestType.withParameters,
                label: TestType.withParameters,
            },
            {
                value: TestType.withGeneratedData,
                label: TestType.withGeneratedData,
            },
            {
                value: "rerunPrevious",
                label: "rerunPrevious",
                isDisabled: !testingState.action,
            },
        ],
        [testingState.action],
    );
    const [preset, setPreset] = useState<Preset>();
    const predefinedTestType = useSelector(getTestType);
    useEffect(() => {
        setPreset(() => {
            return presets.find((p) => {
                return p.value === predefinedTestType;
            });
        });
    }, [predefinedTestType, presets, testingState.action]);

    // Availability of adhoc testing
    const adhocTestIsAvailable = useAdhocTestingAvailability(disabled);

    // Availability of generated data testing
    const testCapabilities = useSelector(getTestCapabilities);
    const processIsLatestVersion = useSelector(isLatestProcessVersion);
    const testFromGeneratedDataIsAvailable =
        !disabled && processIsLatestVersion && testCapabilities && testCapabilities.canGenerateTestData;

    const atLeastOneTypeOfTestIsAvailable = adhocTestIsAvailable || testFromGeneratedDataIsAvailable;

    const dispatch = useDispatch();
    const openDialog = useCallback(
        (preset?: Preset) => {
            setPreset((previous) => preset ?? previous);
            if (preset?.value === "rerunPrevious") {
                testingState.action();
            } else {
                if (preset?.value) {
                    dispatch({
                        type: "UPDATE_TEST_TYPE",
                        testType: preset.value,
                    });
                }
                open<TestingData>({
                    id: "scenarioTest",
                    title: t("dialog.title.scenarioTest", "Scenario test"),
                    isResizable: true,
                    isModal: true,
                    kind: WindowKind.scenarioTest,
                    meta: {
                        viewParams: {
                            Icon: TestingIcon,
                            docs,
                            markdownContent,
                        },
                        testingState,
                    },
                });
            }
        },
        [dispatch, docs, markdownContent, open, t, testingState],
    );

    const isLoading = useSelector(getTestResultsLoading);

    const { variant } = useContext(ToolbarButtonsContext);
    const side = useContext(ToolbarSideContext);

    const tooltip: string = disabled
        ? t(
              "panels.actions.scenarioTest.button.testing-not-available-in-current-state-title",
              "Scenario testing is not supported for scenario in current state",
          )
        : !atLeastOneTypeOfTestIsAvailable
        ? t(
              "panels.actions.scenarioTest.button.testing-not-available-for-current-sources-title",
              "Scenario testing is not supported for currently configured sources",
          )
        : title;

    return (
        <ToolbarButton
            name={`${name || t("panels.actions.scenarioTest.button.name", "Test scenario")} ${preset?.label || ""}`}
            title={tooltip || t("panels.actions.scenarioTest.button.title", "run test")}
            icon={<TestingIcon />}
            sx={(theme) => {
                const normal = theme.palette.primary.main;
                const highlight = theme.palette.primary.light;
                const isHorizontal = variant === ButtonsVariant.xs && [ToolbarsSide.CenterTop, ToolbarsSide.CenterBottom].includes(side);
                return {
                    color: theme.palette.getContrastText(normal),
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
                              minWidth: "12em",
                              textAlign: "left",
                              display: ButtonsVariant.xs === variant ? "inline" : null,
                          }
                        : null,
                };
            }}
            isLoading={isLoading}
            disabled={!atLeastOneTypeOfTestIsAvailable}
            onClick={() => openDialog(preset)}
            type={type}
            presets={presets}
            selected={preset}
            onPresetChange={(value) => openDialog(value)}
        />
    );
}

export default ScenarioTestButton;
