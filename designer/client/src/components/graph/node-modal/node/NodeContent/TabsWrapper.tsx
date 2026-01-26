import { Collapse, Tab, Tabs, Badge } from "@mui/material";
import React, { useState } from "react";

import { InfoTooltip } from "../../editors/InfoTooltip/InfoTooltip";

export interface TabDef {
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
    const [value, setValue] = useState(0);

    const handleChange = (_: React.SyntheticEvent, newValue: number) => {
        setValue(newValue);
    };

    const tabDefs = tabs.filter(({ disabled }) => !disabled || !hideDisabled);
    return (
        <>
            <Collapse in={tabDefs.length > 1 || !hideIfOne}>
                <Tabs
                    value={value}
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
                            key={i}
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
                            id={`tab-${i}`}
                            aria-controls={`tabpanel-${i}`}
                            disabled={t.disabled}
                        />
                    ))}
                    {tabs[value].additionalTabContent}
                </Tabs>
            </Collapse>
            <div role="tabpanel" id={`tabpanel-${value}`} aria-labelledby={`tab-${value}`}>
                {tabs[value].content}
            </div>
        </>
    );
};
