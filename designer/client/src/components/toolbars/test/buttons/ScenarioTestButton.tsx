import { alpha } from "@mui/material";
import React, { useCallback, useContext, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";

import { updateTestType } from "../../../../actions/nk/displayTestResults";
import TestingIcon from "../../../../assets/img/toolbarButtons/test.svg";
import { TestCapabilityStatus } from "../../../../common/TestResultUtils";
import { getTestCapabilities, getTestResultsLoading, getTestType, isLatestProcessVersion } from "../../../../reducers/selectors/graph";
import { ToolbarsSide } from "../../../../reducers/toolbars";
import { useWindows, WindowKind } from "../../../../windowManager";
import { getHasPendingChanges } from "../../../graph/node-modal/node/useEditState";
import { useAdhocTestingAvailability } from "../../../modals/AdhocTesting/useAdhocTestingAvailability";
import { useTestingState } from "../../../modals/Testing/TestingContext";
import type { TestingData, TestingViewParams } from "../../../modals/Testing/TestingDialog";
import { ButtonsVariant, ToolbarButton, ToolbarButtonsContext } from "../../../toolbarComponents/toolbarButtons";
import { ToolbarSideContext } from "../../../toolbarComponents/ToolbarsContainer";
import type { CustomButtonTypes, PropsOfButton } from "../../../toolbarSettings/buttons";

export type ScenarioTestButtonProps = {
    type: CustomButtonTypes.scenarioTest;
    name?: string;
    title?: string;
    docs?: TestingViewParams["docs"];
    markdownContent?: TestingViewParams["markdownContent"];
};

const RERUN_PREVIOUS = "rerunPrevious";

type Preset = {
    value: string;
    label: string;
    isDisabled?: boolean;
};

function ScenarioTestButton({ disabled, name, title, docs, markdownContent, type }: PropsOfButton<CustomButtonTypes.scenarioTest>) {
    const { t } = useTranslation();
    const { open } = useWindows();

    const testingState = useTestingState();

    const presets: Preset[] = useMemo(() => {
        const retest = {
            label: t("testingForm.retest.menu.label", "Retest scenario"),
            value: RERUN_PREVIOUS,
            isDisabled: !testingState.action,
        };
        const options = testingState.options.map(({ value, menuLabel, disabled }) => ({
            value,
            label: menuLabel,
            isDisabled: disabled,
        }));
        return [...options, retest];
    }, [t, testingState.action, testingState.options]);

    const storedTestType = useSelector(getTestType);
    const [preset, setPreset] = useState<Preset>();
    useEffect(() => {
        setPreset((prev) => {
            const expected = testingState.action && storedTestType === prev?.value ? RERUN_PREVIOUS : storedTestType;
            return presets.find((p) => p.value === expected);
        });
    }, [storedTestType, presets, testingState.action]);

    // Availability of adhoc testing
    const adhocTestIsAvailable = useAdhocTestingAvailability(disabled);

    // Availability of generated data testing
    const testCapabilities = useSelector(getTestCapabilities);
    const processIsLatestVersion = useSelector(isLatestProcessVersion);
    const testFromGeneratedDataIsAvailable =
        !disabled && processIsLatestVersion && testCapabilities?.testWithGeneratedData.status === TestCapabilityStatus.AVAILABLE;

    const atLeastOneTypeOfTestIsAvailable = adhocTestIsAvailable || testFromGeneratedDataIsAvailable;

    const hasPendingChanges = useSelector(getHasPendingChanges);

    const dispatch = useDispatch();
    const openDialog = useCallback(
        (preset?: Preset) => {
            if (preset?.value === RERUN_PREVIOUS) {
                testingState.action();
                setPreset(preset);
                return;
            }
            dispatch(updateTestType(preset?.value));
            open<TestingData>({
                id: "scenarioTest",
                title: t("dialog.title.scenarioTest", "Scenario test"),
                isResizable: true,
                isModal: true,
                kind: WindowKind.scenarioTest,
                meta: {
                    storeAction: testingState.handleSetAction,
                    viewParams: {
                        Icon: TestingIcon,
                        docs,
                        markdownContent,
                    },
                },
            });
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
            name={
                preset?.value === RERUN_PREVIOUS
                    ? t("panels.actions.scenarioTest.button.nameAlt", "Retest")
                    : name || t("panels.actions.scenarioTest.button.name", "Test")
            }
            title={tooltip || t("panels.actions.scenarioTest.button.title", "run test")}
            icon={<TestingIcon />}
            sx={(theme) => {
                const normal = theme.palette.primary.main;
                const highlight = theme.palette.primary.light;
                const isHorizontal = variant === ButtonsVariant.xs && [ToolbarsSide.CenterTop, ToolbarsSide.CenterBottom].includes(side);
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
                              fontSize: "16px",
                              minWidth: "5em",
                              display: "inline",
                              textTransform: "initial",
                          }
                        : null,
                };
            }}
            isLoading={isLoading}
            disabled={!atLeastOneTypeOfTestIsAvailable || hasPendingChanges}
            onClick={() => openDialog(preset)}
            type={type}
            presets={presets}
            selected={preset}
            onPresetChange={(value) => openDialog(value)}
        />
    );
}

export default ScenarioTestButton;
