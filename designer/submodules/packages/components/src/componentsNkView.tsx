import React, { memo } from "react";

import type { NkViewProps } from "./common/nkView";
import { NkView } from "./common/nkView";
import { ComponentsRoutes } from "./components/componentsRoutes";

const ComponentsNkView = ({ navigate }: NkViewProps) => (
    <NkView navigate={navigate}>
        <ComponentsRoutes />
    </NkView>
);

export default memo(ComponentsNkView);
