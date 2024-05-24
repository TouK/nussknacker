import React from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import Icon from "../../../../assets/img/toolbarButtons/redo.svg";
import { getHistoryFuture } from "../../../../reducers/selectors/graph";
import { useSelectionActions } from "../../../graph/SelectionContextProvider";
import { CapabilitiesToolbarButton } from "../../../toolbarComponents/CapabilitiesToolbarButton";
import { ToolbarButtonProps } from "../../types";

function RedoButton(props: ToolbarButtonProps): JSX.Element {
    const { redo } = useSelectionActions();
    const future = useSelector(getHistoryFuture);
    const { t } = useTranslation();
    const { disabled, type } = props;
    const available = !disabled && future.length > 0 && redo;

    return (
        <CapabilitiesToolbarButton
            editFrontend
            name={t("panels.actions.edit-redo.button", "redo")}
            disabled={!available}
            icon={<Icon />}
            onClick={available ? (e) => redo(e.nativeEvent) : null}
            type={type}
        />
    );
}

export default RedoButton;
