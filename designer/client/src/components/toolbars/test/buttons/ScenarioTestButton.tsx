import { alpha } from "@mui/material";
import React, { useCallback, useContext, useMemo } from "react";
import { useTranslation } from "react-i18next";

import { testScenarioWithTestCase } from "../../../../actions/nk/testingActions";
import TestingIcon from "../../../../assets/img/toolbarButtons/test.svg";
import { convertViewportUnitToPixels } from "../../../../common/convertViewportUnitToPixels";
import { hasInputDataRecordsDefined, getTestCase } from "../../../../reducers/selectors/testCases";
import { getTestResultsLoading } from "../../../../reducers/selectors/testing";
import { getUserSettings } from "../../../../reducers/selectors/userSettings";
import { ToolbarsSide } from "../../../../reducers/toolbars";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import { useWindows } from "../../../../windowManager/useWindows";
import { WindowKind } from "../../../../windowManager/WindowKind";
import type { TestingData, TestingViewParams } from "../../../modals/TestingDataRecords/Dialog";
import { useTestingScenarioEnabled } from "../../../modals/TestingDataRecords/useTestingScenarioEnabled";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons/ToolbarButton";
import { ButtonsVariant, ToolbarButtonsContext } from "../../../toolbarComponents/toolbarButtons/ToolbarButtons";
import { ToolbarSideContext } from "../../../toolbarComponents/ToolbarsContainer";
import type { CustomButtonTypes } from "../../../toolbarSettings/buttons/buttonsMap";
import type { PropsOfButton } from "../../../toolbarSettings/buttons/types";

export type ScenarioTestButtonProps = {
    type: CustomButtonTypes.scenarioTest;
    name?: string;
    title?: string;
    titleOverride?: string;
    docs?: TestingViewParams["docs"];
    markdownContent?: TestingViewParams["markdownContent"];
};

const RUN_NEW_TEST = "reNewTest";
const RERUN_LAST_TEST = "rerunLastTest";

type Preset = {
    value: string;
    label: string;
    isDisabled?: boolean;
};

function ScenarioTestButton(props: PropsOfButton<CustomButtonTypes.scenarioTest>) {
    const settings = useAppSelector(getUserSettings);
    const { disabled, name, title, titleOverride, docs, markdownContent, type } = props;
    const { t } = useTranslation();
    const { open } = useWindows();
    const testCase = useAppSelector(getTestCase);
    const testingDataRecordsDefined = useAppSelector(hasInputDataRecordsDefined);
    const dispatch = useAppDispatch();

    const handleRerunLastTest = useCallback(() => {
        return dispatch(testScenarioWithTestCase(testCase, settings["node.showMockFieldOnEnrichers"]));
    }, [dispatch, settings, testCase]);

    const presets: Preset[] = useMemo(() => {
        return [
            {
                label: t("testingForm.test.menu.label", "Run a new test"),
                value: RUN_NEW_TEST,
            },
            {
                label: t("testingForm.retest.menu.label", "Rerun last test"),
                value: RERUN_LAST_TEST,
                isDisabled: !testingDataRecordsDefined,
            },
        ];
    }, [t, testingDataRecordsDefined]);

    const isLoading = useAppSelector(getTestResultsLoading);

    const presetActionOnButtonClick = useMemo(() => {
        return testingDataRecordsDefined ? presets[1] : presets[0];
    }, [presets, testingDataRecordsDefined]);

    const testingScenarioEnabled = useTestingScenarioEnabled({ disabled });

    const openDialog = useCallback(
        (preset?: Preset) => {
            if (preset?.value === RERUN_LAST_TEST) {
                handleRerunLastTest();
                return;
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
                },
                layoutData: {
                    minWidth: 1200,
                    height: convertViewportUnitToPixels("80vh"),
                },
            });
        },
        [docs, handleRerunLastTest, markdownContent, open, t],
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
            : !testingScenarioEnabled
            ? t(
                  "panels.actions.scenarioTest.button.testing-not-available-for-current-sources-title",
                  "Scenario testing is not supported for currently configured sources",
              )
            : title);

    return (
        <ToolbarButton
            name={
                testingDataRecordsDefined
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
            disabled={!testingScenarioEnabled || isLoading}
            onClick={() => openDialog(presetActionOnButtonClick)}
            type={type}
            presets={presets}
            selected={presetActionOnButtonClick}
            onPresetChange={(value) => openDialog(value)}
        />
    );
}

export default ScenarioTestButton;
