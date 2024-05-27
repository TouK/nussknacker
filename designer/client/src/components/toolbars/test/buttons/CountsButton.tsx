import React from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import Icon from "../../../../assets/img/toolbarButtons/counts.svg";
import { isFragment } from "../../../../reducers/selectors/graph";
import { getFeatureSettings } from "../../../../reducers/selectors/settings";
import { useWindows, WindowKind } from "../../../../windowManager";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import { ToolbarButtonProps } from "../../types";

// TODO: counts and metrics should not be visible in archived process
function CountsButton(props: ToolbarButtonProps) {
    const { t } = useTranslation();
    const featuresSettings = useSelector(getFeatureSettings);
    const fragment = useSelector(isFragment);
    const { open } = useWindows();
    const { disabled, type } = props;

    return featuresSettings?.counts && !fragment ? (
        <ToolbarButton
            name={t("panels.actions.test-counts.name", "counts")}
            title={t("panels.actions.test-counts.button.title", "count node invocations in given period")}
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
