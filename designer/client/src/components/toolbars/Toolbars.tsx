import type { PropsWithChildren } from "react";
import React, { memo } from "react";

import { getScenario } from "../../reducers/selectors/graph";
import { useAppSelector } from "../../store/storeHelpers";
import SpinnerWrapper from "../spinner/SpinnerWrapper";
import ToolbarsLayer from "../toolbarComponents/ToolbarsLayer";
import { useToolbarConfig } from "../toolbarSettings/useToolbarConfig";

type Props = PropsWithChildren<{
    isReady: boolean;
    externalLayerWrapper?: React.ComponentType<PropsWithChildren>;
}>;

const Toolbars = ({ isReady, ...passProps }: Props) => {
    const scenario = useAppSelector(getScenario);
    const [toolbars, toolbarsConfigId] = useToolbarConfig();
    return (
        <SpinnerWrapper isReady={isReady && !!scenario}>
            <ToolbarsLayer toolbars={toolbars} configId={toolbarsConfigId} {...passProps} />
        </SpinnerWrapper>
    );
};

export default memo(Toolbars);
