import React, { useMemo } from "react";
import { useSelector } from "react-redux";
import { getProcessName, isDeployedVersion } from "../../../reducers/selectors/graph";
import { getProcessState } from "../../../reducers/selectors/scenarioState";
import { getCustomActions } from "../../../reducers/selectors/settings";
import CustomActionButton from "../../toolbars/scenarioActions/buttons/CustomActionButton";
import { CustomButtonTypes } from "./CustomButtonTypes";

export interface ActionButtonProps {
    name: string;
    type?: CustomButtonTypes;
}

export function ActionButton({ name, type }: ActionButtonProps): JSX.Element {
    const processName = useSelector(getProcessName);
    const status = useSelector(getProcessState)?.status;
    const customActions = useSelector(getCustomActions);
    const action = useMemo(() => customActions.find((a) => a.name === name), [customActions, name]);

    // FIXME: This part requires further changes within periodic scenario engine.
    // Currently we use experimental api of custom actions for periodic scenarios (an experimental engine).
    // Part of this experimental engine allows to run immediately scheduled scenario. This activity will be moved inside core deployment operations and aligned with other deployment engines.
    // Here we want to disable that one action button in confusing situation when user looks at scenario version that is not currently deployed.
    const isDeployed = useSelector(isDeployedVersion);
    const disabledValue = useMemo(() => !isDeployed, [isDeployed, name]);

    return action ? (
        <CustomActionButton
            action={action}
            processName={processName}
            processStatus={status}
            disabled={name === "run now" ? disabledValue : false}
            type={type}
        />
    ) : null;
}
