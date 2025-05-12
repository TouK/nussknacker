import React, { useMemo } from "react";
import { useSelector } from "react-redux";
import { Navigate } from "react-router-dom";

import { getTabs } from "../reducers/selectors/settings";
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
    const customTabs = useSelector(getTabs);
    return useMemo(() => customTabs.find((tab) => tab.id === id), [customTabs, id]);
}

export function CustomTabPage<P extends Record<string, unknown>>({ id, ...props }: { id?: string } & P): JSX.Element {
    const tab = useTabData(id);
    return tab ? <CustomTabWrapper tab={tab} {...props} /> : <Navigate to="/404" />;
}
