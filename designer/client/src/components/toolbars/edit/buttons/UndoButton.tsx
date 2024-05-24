import React from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import Icon from "../../../../assets/img/toolbarButtons/undo.svg";
import { getHistoryPast } from "../../../../reducers/selectors/graph";
import { useSelectionActions } from "../../../graph/SelectionContextProvider";
import { CapabilitiesToolbarButton } from "../../../toolbarComponents/CapabilitiesToolbarButton";
import { ToolbarButtonProps } from "../../types";

function UndoButton(props: ToolbarButtonProps): JSX.Element {
    const { undo } = useSelectionActions();
    const past = useSelector(getHistoryPast);
    const { t } = useTranslation();
    const { disabled, type } = props;
    const available = !disabled && past.length > 0 && undo;

    return (
        <CapabilitiesToolbarButton
            editFrontend
            name={t("panels.actions.edit-undo.button", "undo")}
            disabled={!available}
            icon={<Icon />}
            onClick={available ? (e) => undo(e.nativeEvent) : null}
            type={type}
        />
    );
}

export default UndoButton;
