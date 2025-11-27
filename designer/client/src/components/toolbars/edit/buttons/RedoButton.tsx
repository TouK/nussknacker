import React from "react";
import { useTranslation } from "react-i18next";

import Icon from "../../../../assets/img/toolbarButtons/redo.svg";
import { getHistoryCounts } from "../../../../reducers/selectors/getHistory";
import { useAppSelector } from "../../../../store/storeHelpers";
import { useSelectionActions } from "../../../graph/SelectionContextProvider";
import type { ToolbarButtonProps } from "../../types";
import { CounterToolbarButton } from "./CounterToolbarButton";

function RedoButton(props: ToolbarButtonProps): React.JSX.Element {
    const { redo } = useSelectionActions();
    const [, future] = useAppSelector(getHistoryCounts);
    const { t } = useTranslation();
    const { disabled, type } = props;
    const available = !disabled && redo;

    return (
        <CounterToolbarButton
            editFrontend
            name={t("panels.actions.edit-redo.button", "redo")}
            disabled={!available}
            icon={<Icon />}
            onClick={available ? (e) => redo(e.nativeEvent) : null}
            type={type}
            count={future}
        />
    );
}

export default RedoButton;
