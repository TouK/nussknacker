import { alpha } from "@mui/material";
import React, { useCallback, useContext, useMemo } from "react";
import { useTranslation } from "react-i18next";

import { updateTestType } from "../../../../actions/nk/displayTestResults";
import TestingIcon from "../../../../assets/img/toolbarButtons/test.svg";
import { TestCapabilityStatus } from "../../../../common/TestResultUtils";
import {
    getPerformedTestType,
    getTestCapabilities,
    getTestResultsLoading,
    isLatestProcessVersion,
} from "../../../../reducers/selectors/graph";
import { ToolbarsSide } from "../../../../reducers/toolbars";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import { useWindows, WindowKind } from "../../../../windowManager";
import { getHasPendingChanges } from "../../../graph/node-modal/node/useEditState";
import { useAdhocTestingAvailability } from "../../../modals/AdhocTesting/useAdhocTestingAvailability";
import type { TestingData, TestingViewParams } from "../../../modals/Testing/TestingDialog";
import { ButtonsVariant, ToolbarButton, ToolbarButtonsContext } from "../../../toolbarComponents/toolbarButtons";
import { ToolbarSideContext } from "../../../toolbarComponents/ToolbarsContainer";
import type { CustomButtonTypes, PropsOfButton } from "../../../toolbarSettings/buttons";
import { useTestingButtonContext } from "./TestButtonContext";

export type ScenarioTestButtonProps = {
    type: CustomButtonTypes.scenarioTest;
    name?: string;
    title?: string;
    titleOverride?: string;
    docs?: TestingViewParams["docs"];
    markdownContent?: TestingViewParams["markdownContent"];
};

const RERUN_PREVIOUS = "rerunPrevious";

type Preset = {
    value: string;
    label: string;
    isDisabled?: boolean;
};

function ScenarioTestButton(props: PropsOfButton<CustomButtonTypes.scenarioTest>) {
    const { disabled, name, title, titleOverride, docs, markdownContent, type } = props;
    const { t } = useTranslation();
    const { open } = useWindows();

    const testingState = useTestingButtonContext();

    const presets: Preset[] = useMemo(() => {
        const retest = {
            label: t("testingForm.retest.menu.label", "Rerun last test"),
            value: RERUN_PREVIOUS,
            isDisabled: !testingState.action,
        };

        const options = testingState.options
            .filter((o) => !o.disabled)
            .slice(0, 1)
            .map(({ value, menuLabel = t("testingForm.test.menu.label", "Run a new test"), disabled }) => ({
                value,
                label: menuLabel,
                isDisabled: disabled,
            }));

        return [...options, retest];
    }, [t, testingState.action, testingState.options]);

    const performedTestType = useAppSelector(getPerformedTestType);
    const isLoading = useAppSelector(getTestResultsLoading);

    const preset = useMemo(() => {
        return presets.find((p) => p.value === performedTestType);
    }, [performedTestType, presets]);

    // Availability of adhoc testing
    const adhocTestIsAvailable = useAdhocTestingAvailability(disabled);

    // Availability of live data testing
    const testCapabilities = useAppSelector(getTestCapabilities);
    const processIsLatestVersion = useAppSelector(isLatestProcessVersion);
    const testFromLiveDataIsAvailable =
        !disabled && processIsLatestVersion && testCapabilities?.testWithLiveData.status === TestCapabilityStatus.AVAILABLE;

    const atLeastOneTypeOfTestIsAvailable = adhocTestIsAvailable || testFromLiveDataIsAvailable;

    const hasPendingChanges = useAppSelector(getHasPendingChanges);

    const dispatch = useAppDispatch();
    const openDialog = useCallback(
        (preset?: Preset) => {
            if (preset?.value === RERUN_PREVIOUS) {
                testingState.action();
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

    const { variant } = useContext(ToolbarButtonsContext);
    const side = useContext(ToolbarSideContext);

    const tooltip: string =
        titleOverride ??
        (disabled
            ? t(
                  "panels.actions.scenarioTest.button.testing-not-available-in-current-state-title",
                  "Scenario testing is not supported for scenario in current state",
              )
            : !atLeastOneTypeOfTestIsAvailable
            ? t(
                  "panels.actions.scenarioTest.button.testing-not-available-for-current-sources-title",
                  "Scenario testing is not supported for currently configured sources",
              )
            : title);

    return (
        <ToolbarButton
            name={
                preset?.value === RERUN_PREVIOUS
                    ? t("panels.actions.scenarioTest.button.nameAlt", "Rerun test")
                    : name || t("panels.actions.scenarioTest.button.name", "Test")
            }
            title={tooltip || t("panels.actions.scenarioTest.button.title", "run test")}
            icon={<TestingIcon />}
            sx={(theme) => {
                const normal = theme.palette.primary.main;
                const highlight = theme.palette.primary.light;
                const isHorizontal =
                    variant === ButtonsVariant.xs &&
                    [ToolbarsSide.CenterTop, ToolbarsSide.CenterBottom, ToolbarsSide.AboveNodeWindow].includes(side);
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
            }}
            isLoading={isLoading}
            disabled={!atLeastOneTypeOfTestIsAvailable || hasPendingChanges || isLoading}
            onClick={() => openDialog(preset)}
            type={type}
            presets={presets}
            selected={preset}
            onPresetChange={(value) => openDialog(value)}
        />
    );
}

export default ScenarioTestButton;
