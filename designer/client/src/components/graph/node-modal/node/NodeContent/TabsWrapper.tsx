import { Collapse, Tab, Tabs } from "@mui/material";
import { omit } from "lodash";
import React, { useEffect, useState } from "react";

import { replaceSearchQuery } from "../../../../../containers/hooks/useSearchQuery";
import { LabelWithErrorIndicator } from "../../../../common/LabelWithErrorIndicator";

export const ACTIVE_TAB_QUERY_KEY = "activeTab";

export enum NodeDetailsTab {
    "general" = "general",
    "testing" = "testing",
}

export interface TabDef {
    id: string;
    label: string;
    content: React.ReactNode;
    disabled?: boolean;
    additionalTabContent?: React.ReactNode;
    showErrorIndicator?: boolean;
}

interface Props {
    tabs: TabDef[];
    hideDisabled?: boolean;
    hideIfOne?: boolean;
}

export const TabsWrapper = ({ tabs, hideDisabled, hideIfOne }: Props) => {
    const tabDefs = tabs.filter(({ disabled }) => !disabled || !hideDisabled);

    const [activeTabId, setActiveTabId] = useState<string>(() => {
        const params = new URLSearchParams(window.location.search);
        const fromUrl = params.get(ACTIVE_TAB_QUERY_KEY);
        return tabDefs.find((tab) => tab.id === fromUrl)?.id ?? tabDefs[0]?.id ?? "";
    });

    useEffect(() => {
        return () => {
            replaceSearchQuery((current) => omit(current, ACTIVE_TAB_QUERY_KEY));
        };
    }, []);

    // Fallback to first tab if invalid or missing
    const activeIndex = Math.max(
        0,
        tabDefs.findIndex((tab) => tab.id === activeTabId),
    );

    const handleChange = (_: React.SyntheticEvent, newIndex: number) => {
        const newTabId = tabDefs[newIndex].id;
        replaceSearchQuery((current) => ({ ...current, activeTab: newTabId }));
        setActiveTabId(newTabId);
    };

    return (
        <>
            <Collapse in={tabDefs.length > 1 || !hideIfOne}>
                <Tabs
                    value={activeIndex}
                    onChange={handleChange}
                    sx={{
                        "& .MuiTab-root": {
                            outline: "none",
                        },
                        "& .MuiTab-root.Mui-focusVisible": {
                            outline: "none",
                        },
                    }}
                >
                    {tabDefs.map((t, i) => (
                        <Tab
                            key={t.id}
                            id={`tab-${i}`}
                            aria-controls={`tabpanel-${i}`}
                            disabled={t.disabled}
                            label={
                                <LabelWithErrorIndicator
                                    label={t.label}
                                    hasError={t.showErrorIndicator}
                                    errorTooltip={`There are errors in a ${t.label} data`}
                                />
                            }
                        />
                    ))}
                    {tabs[activeIndex].additionalTabContent}
                </Tabs>
            </Collapse>

            <div role="tabpanel" id={`tabpanel-${activeIndex}`} aria-labelledby={`tab-${activeIndex}`}>
                {tabDefs[activeIndex]?.content}
            </div>
        </>
    );
};
