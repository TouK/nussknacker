import React from "react";
import { useTranslation } from "react-i18next";

import Icon from "../../../../assets/img/toolbarButtons/undo.svg";
import { getHistoryCounts } from "../../../../reducers/selectors/getHistory";
import { useAppSelector } from "../../../../store/storeHelpers";
import { useSelectionActions } from "../../../graph/SelectionContextProvider";
import type { ToolbarButtonProps } from "../../types";
import { CounterToolbarButton } from "./CounterToolbarButton";

function UndoButton(props: ToolbarButtonProps): React.JSX.Element {
    const { undo } = useSelectionActions();
    const [past] = useAppSelector(getHistoryCounts);
    const { t } = useTranslation();
    const { disabled, type } = props;
    const available = !disabled && undo;

    return (
        <CounterToolbarButton
            editFrontend
            name={t("panels.actions.edit-undo.button", "undo")}
            disabled={!available}
            icon={<Icon />}
            onClick={available ? (e) => undo(e.nativeEvent) : null}
            type={type}
            count={past}
        />
    );
}

export default UndoButton;
