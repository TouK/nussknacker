import React from "react";
import Inspector, { chromeDark } from "react-inspector";
import { useSelector } from "react-redux";

import { getLiveData, isReadyForLiveData } from "../../reducers/selectors/getLiveData";
import { getProcessVersionId, isDeployed, isPristine } from "../../reducers/selectors/graph";
import { DebugBox } from "../DebugBox";

export const DebugComponent = () => {
    const version = useSelector(getProcessVersionId);
    const clean = useSelector(isPristine);
    const deployed = useSelector(isDeployed);
    const readyForResults = useSelector(isReadyForLiveData);
    const liveData = useSelector(getLiveData);
    return (
        <DebugBox>
            <Inspector
                theme={{
                    ...chromeDark,
                    BASE_BACKGROUND_COLOR: "transparent",
                    TREENODE_FONT_SIZE: "14px",
                }}
                data={{
                    version,
                    clean,
                    deployed,
                    readyForResults,
                    liveData,
                }}
            />
        </DebugBox>
    );
};
