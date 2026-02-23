import { Collapse, Tab, Tabs, Badge } from "@mui/material";
import { omit } from "lodash";
import React, { useEffect } from "react";

import { replaceSearchQuery, useSearchQueryParam } from "../../../../../containers/hooks/useSearchQuery";
import { InfoTooltip } from "../../editors/InfoTooltip/InfoTooltip";

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

    const activeTabId = useSearchQueryParam("activeTab");

    useEffect(() => {
        return () => {
            replaceSearchQuery((current) => omit(current, "activeTab"));
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
                                t.showErrorIndicator ? (
                                    <span style={{ position: "relative", display: "inline-block", paddingRight: "8px" }}>
                                        <span>{t.label}</span>
                                        <InfoTooltip title={`There are errors in a ${t.label} data`} variant="hover">
                                            <Badge
                                                color="error"
                                                variant="dot"
                                                overlap="rectangular"
                                                sx={{ position: "absolute", top: 0, right: 0 }}
                                            >
                                                {/* empty anchor for the badge so tooltip triggers only on the dot */}
                                                <span style={{ display: "inline-block", width: 0, height: 0 }} />
                                            </Badge>
                                        </InfoTooltip>
                                    </span>
                                ) : (
                                    t.label
                                )
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
