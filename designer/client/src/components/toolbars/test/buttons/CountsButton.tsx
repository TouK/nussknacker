import React from "react";
import { useTranslation } from "react-i18next";

import Icon from "../../../../assets/img/toolbarButtons/counts.svg";
import { getProcessCountsRefresh, isFragment } from "../../../../reducers/selectors/graph";
import { getFeatureSettings } from "../../../../reducers/selectors/settings";
import { useAppSelector } from "../../../../store/storeHelpers";
import { useWindows } from "../../../../windowManager/useWindows";
import { WindowKind } from "../../../../windowManager/WindowKind";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons/ToolbarButton";
import type { ToolbarButtonProps } from "../../types";
import { useProgress } from "./useProgress";

// TODO: counts and metrics should not be visible in archived process
function CountsButton(props: ToolbarButtonProps) {
    const { t } = useTranslation();
    const featuresSettings = useAppSelector(getFeatureSettings);
    const refresh = useAppSelector(getProcessCountsRefresh);
    const fragment = useAppSelector(isFragment);
    const { open } = useWindows();
    const { disabled, type } = props;

    const { percent, isProgressing } = useProgress(refresh);

    return featuresSettings?.counts && !fragment ? (
        <ToolbarButton
            isLoading={isProgressing}
            loadingProgress={percent}
            name={t("panels.actions.test-counts.name", "counts")}
            title={t("panels.actions.test-counts.button.title", "Show number of processed data records")}
            icon={<Icon />}
            disabled={disabled}
            onClick={() =>
                open({
                    kind: WindowKind.calculateCounts,
                })
            }
            type={type}
        />
    ) : null;
}

export default CountsButton;
