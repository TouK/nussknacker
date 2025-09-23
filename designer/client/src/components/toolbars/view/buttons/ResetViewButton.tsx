import React from "react";
import { useTranslation } from "react-i18next";

import { resetToolbars } from "../../../../actions/nk/toolbars";
import Icon from "../../../../assets/img/toolbarButtons/resetgui.svg";
import { getToolbarsConfigId } from "../../../../reducers/selectors/toolbars";
import { useAppDispatch, useAppSelector } from "../../../../store/storeHelpers";
import { useGraph } from "../../../graph/GraphContext";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons/ToolbarButton";
import type { ToolbarButtonProps } from "../../types";

function ResetViewButton(props: ToolbarButtonProps) {
    const { t } = useTranslation();
    const dispatch = useAppDispatch();
    const configId = useAppSelector(getToolbarsConfigId);
    const graphGetter = useGraph();

    const { disabled, type } = props;

    return (
        <ToolbarButton
            name={t("panels.actions.view-reset.label", "reset")}
            icon={<Icon />}
            disabled={disabled}
            onClick={() => {
                dispatch(resetToolbars(configId));
                graphGetter?.()?.fit();
            }}
            type={type}
        />
    );
}

export default ResetViewButton;
