import React from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { resetToolbars } from "../../../../actions/nk/toolbars";
import Icon from "../../../../assets/img/toolbarButtons/resetgui.svg";
import { getToolbarsConfigId } from "../../../../reducers/selectors/toolbars";
import { ToolbarButton } from "../../../toolbarComponents/toolbarButtons";
import { ToolbarButtonProps } from "../../types";
import { useGraph } from "../../../graph/GraphContext";

export function ResetViewButton(props: ToolbarButtonProps) {
    const { t } = useTranslation();
    const dispatch = useDispatch();
    const configId = useSelector(getToolbarsConfigId);
    const graphGetter = useGraph();

    const { disabled } = props;

    return (
        <ToolbarButton
            name={t("panels.actions.view-reset.label", "reset")}
            icon={<Icon />}
            disabled={disabled}
            onClick={() => {
                dispatch(resetToolbars(configId));
                graphGetter?.()?.fit();
            }}
        />
    );
}
