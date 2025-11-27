import React, { useMemo } from "react";
import { Navigate } from "react-router-dom";

import { getTabs } from "../reducers/selectors/settings";
import { useAppSelector } from "../store/storeHelpers";
import type { BaseTab } from "./DynamicTab";
import { DynamicTab } from "./DynamicTab";
import { Page } from "./Page";

export function CustomTabWrapper<P extends BaseTab>(props: P) {
    return (
        <Page>
            <DynamicTab {...props} />
        </Page>
    );
}

export function useTabData(id: string) {
    const customTabs = useAppSelector(getTabs);
    return useMemo(() => customTabs.find((tab) => tab.id === id), [customTabs, id]);
}

export function CustomTabPage<P extends Record<string, unknown>>({ id, ...props }: { id?: string } & P): React.JSX.Element {
    const tab = useTabData(id);
    return tab ? <CustomTabWrapper tab={tab} {...props} /> : <Navigate to="/404" />;
}
