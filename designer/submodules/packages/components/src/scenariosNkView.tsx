import React, { memo } from "react";

import type { NkViewProps } from "./common/nkView";
import { NkView } from "./common/nkView";
import type { ScenariosViewProps } from "./scenarios/scenariosView";
import { ScenariosView } from "./scenarios/scenariosView";

const ScenariosNkView = ({ navigate, ...props }: NkViewProps & ScenariosViewProps) => (
    <NkView navigate={navigate}>
        <ScenariosView {...props} />
    </NkView>
);
export default memo(ScenariosNkView);
