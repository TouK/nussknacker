import React, { memo } from "react";
import { NkView, NkViewProps } from "./common";
import { ComponentsRoutes } from "./components/componentsRoutes";

const ComponentsNkView = ({ navigate }: NkViewProps) => (
    <NkView navigate={navigate}>
        <ComponentsRoutes />
    </NkView>
);

export default memo(ComponentsNkView);
