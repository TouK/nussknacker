import { useTranslation } from "react-i18next";

import { useTestingScenarioEnabled } from "../../../../modals/TestingDataRecords/useTestingScenarioEnabled";

interface Props {
    disabled?: boolean;
    title?: string;
    titleOverride?: string;
}

export const useScenarioTestTooltip = ({ disabled, title, titleOverride }: Props): string => {
    const { t } = useTranslation();
    const testingScenarioEnabled = useTestingScenarioEnabled({ disabled });

    return (
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
            : title ?? "")
    );
};
