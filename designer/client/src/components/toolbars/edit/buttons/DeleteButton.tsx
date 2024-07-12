import React from "react";
import { useTranslation } from "react-i18next";
import Icon from "../../../../assets/img/toolbarButtons/delete.svg";
import { useSelectionActions } from "../../../graph/SelectionContextProvider";
import { CapabilitiesToolbarButton } from "../../../toolbarComponents/CapabilitiesToolbarButton";
import { ToolbarButtonProps } from "../../types";

function DeleteButton(props: ToolbarButtonProps): JSX.Element {
    const { t } = useTranslation();
    const { delete: remove } = useSelectionActions();
    const { disabled, type } = props;
    const available = !disabled && remove;

    return (
        <CapabilitiesToolbarButton
            write
            name={t("panels.actions.edit-delete.button", "delete")}
            icon={<Icon />}
            disabled={!available}
            onClick={available ? (e) => remove(e.nativeEvent) : null}
            type={type}
        />
    );
}

export default DeleteButton;
