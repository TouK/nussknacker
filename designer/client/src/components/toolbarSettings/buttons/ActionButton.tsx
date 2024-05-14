import React, { useMemo } from "react";
import { useSelector } from "react-redux";
import { getProcessName } from "../../../reducers/selectors/graph";
import { getProcessState } from "../../../reducers/selectors/scenarioState";
import { getCustomActions } from "../../../reducers/selectors/settings";
import CustomActionButton from "../../toolbars/scenarioActions/buttons/CustomActionButton";

export interface ActionButtonProps {
    name: string;
}

export function ActionButton({ name }: ActionButtonProps): JSX.Element {
    const processName = useSelector(getProcessName);
    const status = useSelector(getProcessState)?.status;
    const customActions = useSelector(getCustomActions);

    const action = useMemo(() => customActions.find((a) => a.name === name), [customActions, name]);

    return action ? <CustomActionButton action={action} processName={processName} processStatus={status} /> : null;
}
