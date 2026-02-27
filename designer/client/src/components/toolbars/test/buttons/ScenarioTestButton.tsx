import { alpha } from "@mui/material";
import React, { useContext, useMemo } from "react";
import { useTranslation } from "react-i18next";

import TestingIcon from "../../../../assets/img/toolbarButtons/test.svg";
import { hasInputDataRecordsDefined } from "../../../../reducers/selectors/testCases";
import { getTestResultsLoading } from "../../../../reducers/selectors/testing";
import { ToolbarsSide } from "../../../../reducers/toolbars";
import { useAppSelector } from "../../../../store/storeHelpers";
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
};

const RUN_ALL = "runAll";
const RERUN_LAST_TEST = "rerunLastTest";

type Preset = {
    value: string;
    label: string;
    isDisabled?: boolean;
};

function ScenarioTestButton(props: PropsOfButton<CustomButtonTypes.scenarioTest>) {
    const { disabled, name, title, titleOverride, type } = props;
    const { t } = useTranslation();

    const presets: Preset[] = useMemo(() => {
        return [
            {
                label: t("testingForm.test.menu.label", "Run all"),
                value: RUN_ALL,
            },
        ];
    }, [t]);

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
            type={type}
            presets={presets}
            selected={presetActionOnButtonClick}
        />
    );
}

export default ScenarioTestButton;
