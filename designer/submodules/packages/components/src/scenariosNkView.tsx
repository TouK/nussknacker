import React, { memo } from "react";
import { NkView, NkViewProps } from "./common";
import { ScenariosView, ScenariosViewProps } from "./scenarios";

const ScenariosNkView = ({ navigate, ...props }: NkViewProps & ScenariosViewProps) => (
    <NkView navigate={navigate}>
        <ScenariosView {...props} />
    </NkView>
);
export default memo(ScenariosNkView);
