import { Collapse, Tab, Tabs } from "@mui/material";
import React, { useState } from "react";

export interface TabDef {
    label: string;
    content: React.ReactNode;
    disabled?: boolean;
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
                        <Tab key={i} label={t.label} id={`tab-${i}`} aria-controls={`tabpanel-${i}`} disabled={t.disabled} />
                    ))}
                </Tabs>
            </Collapse>
            <div role="tabpanel" id={`tabpanel-${value}`} aria-labelledby={`tab-${value}`}>
                {tabs[value].content}
            </div>
        </>
    );
};
