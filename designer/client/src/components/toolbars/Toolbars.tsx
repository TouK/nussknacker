import React, { memo, PropsWithChildren } from "react";
import { useSelector } from "react-redux";
import { getScenario } from "../../reducers/selectors/graph";
import SpinnerWrapper from "../spinner/SpinnerWrapper";
import ToolbarsLayer from "../toolbarComponents/ToolbarsLayer";
import { useToolbarConfig } from "../toolbarSettings/useToolbarConfig";

type Props = PropsWithChildren<{
    isReady: boolean;
}>;

const Toolbars = (props: Props) => {
    const { isReady, children } = props;
    const scenario = useSelector(getScenario);
    const [toolbars, toolbarsConfigId] = useToolbarConfig();

    return (
        <SpinnerWrapper isReady={isReady && !!scenario}>
            <ToolbarsLayer toolbars={toolbars} configId={toolbarsConfigId}>
                {children}
            </ToolbarsLayer>
        </SpinnerWrapper>
    );
};

export default memo(Toolbars);
