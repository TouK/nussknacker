import React, { forwardRef, memo } from "react";
import { useSelector } from "react-redux";
import { getScenario } from "../../reducers/selectors/graph";
import SpinnerWrapper from "../spinner/SpinnerWrapper";
import ToolbarsLayer, { ToolbarsLayerRef } from "../toolbarComponents/ToolbarsLayer";
import { useToolbarConfig } from "../toolbarSettings/useToolbarConfig";

type Props = {
    isReady: boolean;
};

const Toolbars = forwardRef<ToolbarsLayerRef, Props>(function Toolbars(props, forwardedRef) {
    const { isReady } = props;
    const scenario = useSelector(getScenario);
    const [toolbars, toolbarsConfigId] = useToolbarConfig();

    return (
        <SpinnerWrapper isReady={isReady && !!scenario}>
            <ToolbarsLayer ref={console.log} toolbars={toolbars} configId={toolbarsConfigId} />
        </SpinnerWrapper>
    );
});

export default memo(Toolbars);
