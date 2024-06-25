import React, { useMemo } from "react";
import { useSelector } from "react-redux";
import { getProcessName } from "../../../reducers/selectors/graph";
import { getCustomActions } from "../../../reducers/selectors/settings";
import CustomActionButton from "../../toolbars/scenarioActions/buttons/CustomActionButton";
import { CustomButtonTypes } from "./CustomButtonTypes";

export interface ActionButtonProps {
    name: string;
    type?: CustomButtonTypes;
    disabled: boolean;
}

export function ActionButton({ name, type, disabled }: ActionButtonProps): JSX.Element {
    const processName = useSelector(getProcessName);
    const customActions = useSelector(getCustomActions);

    const action = useMemo(() => customActions.find((a) => a.name === name), [customActions, name]);

    return action ? <CustomActionButton action={action} processName={processName} disabled={disabled} type={type}/> : null;
}
