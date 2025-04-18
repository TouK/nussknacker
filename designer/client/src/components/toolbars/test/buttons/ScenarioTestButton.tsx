import loadable from "@loadable/component";
import React, { useCallback, useContext } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";

import { getTestCapabilities, getTestResultsLoading, isLatestProcessVersion } from "../../../../reducers/selectors/graph";
import { ToolbarsSide } from "../../../../reducers/toolbars";
import { useWindows, WindowKind } from "../../../../windowManager";
import { useAdhocTestingAvailability } from "../../../modals/AdhocTesting/useAdhocTestingAvailability";
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

const TestingIcon = loadable(() => import("../../../../assets/img/toolbarButtons/test.svg"));

function ScenarioTestButton({ disabled, name, title, docs, markdownContent, type }: PropsOfButton<CustomButtonTypes.scenarioTest>) {
    const { t } = useTranslation();
    const { open } = useWindows();

    // Availability of adhoc testing
    const adhocTestIsAvailable = useAdhocTestingAvailability(disabled);

    // Availability of generated data testing
    const testCapabilities = useSelector(getTestCapabilities);
    const processIsLatestVersion = useSelector(isLatestProcessVersion);
    const testFromGeneratedDataIsAvailable =
        !disabled && processIsLatestVersion && testCapabilities && testCapabilities.canGenerateTestData;

    const atLeastOneTypeOfTestIsAvailable = adhocTestIsAvailable || testFromGeneratedDataIsAvailable;

    const openDialog = useCallback(() => {
        open<TestingData>({
            title: t("dialog.title.scenarioTest", "Scenario test"),
            isResizable: true,
            kind: WindowKind.scenarioTest,
            meta: {
                viewParams: {
                    Icon: TestingIcon,
                    docs,
                    markdownContent,
                },
            },
        });
    }, [docs, markdownContent, open, t]);

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
            name={name || t("panels.actions.scenarioTest.button.name", "test")}
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
            onClick={openDialog}
            type={type}
        />
    );
}

export default ScenarioTestButton;
