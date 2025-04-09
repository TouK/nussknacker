import loadable from "@loadable/component";
import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";

import { getTestCapabilities, getTestResultsLoading, isLatestProcessVersion } from "../../../../reducers/selectors/graph";
import { useWindows, WindowKind } from "../../../../windowManager";
import { useAdhocTestingAvailability } from "../../../modals/AdhocTesting/useAdhocTestingAvailability";
import type { TestingData, TestingViewParams } from "../../../modals/Testing/TestingDialog";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import type { CustomButtonTypes, PropsOfButton } from "../../../toolbarSettings/buttons";
import { ButtonProgress } from "./ButtonProgress";

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
                viewParams: { Icon: TestingIcon, docs, markdownContent },
            },
        });
    }, [docs, markdownContent, open, t]);

    const isLoading = useSelector(getTestResultsLoading);

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
        <ButtonProgress enabled={isLoading}>
            <ToolbarButton
                name={name || t("panels.actions.scenarioTest.button.name", "test")}
                title={tooltip || t("panels.actions.scenarioTest.button.title", "run test")}
                icon={<TestingIcon />}
                disabled={!atLeastOneTypeOfTestIsAvailable}
                onClick={openDialog}
                type={type}
            />
        </ButtonProgress>
    );
}

export default ScenarioTestButton;
