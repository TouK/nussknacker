import React, { memo } from "react";
import { useSelector } from "react-redux";
import { getScenario } from "../../reducers/selectors/graph";
import SpinnerWrapper from "../spinner/SpinnerWrapper";
import ToolbarsLayer from "../toolbarComponents/ToolbarsLayer";
import { useToolbarConfig } from "../toolbarSettings/useToolbarConfig";

type Props = {
    isReady: boolean;
};

function Toolbars(props: Props) {
    const { isReady } = props;
    const fetchedProcessDetails = useSelector(getScenario);
    const [toolbars, toolbarsConfigId] = useToolbarConfig();

    return (
        <SpinnerWrapper isReady={isReady && !!fetchedProcessDetails}>
            <ToolbarsLayer toolbars={toolbars} configId={toolbarsConfigId} />
        </SpinnerWrapper>
    );
}

export default memo(Toolbars);
