import { Tab, Tabs } from "@mui/material";
import React, { useState } from "react";

interface TabDef {
    label: string;
    content: React.ReactNode;
}

interface Props {
    tabs: TabDef[];
}

export const TabsWrapper = ({ tabs }: Props) => {
    const [value, setValue] = useState(0);

    const handleChange = (_: React.SyntheticEvent, newValue: number) => {
        setValue(newValue);
    };

    return (
        <>
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
                {tabs.map((t, i) => (
                    <Tab key={i} label={t.label} id={`tab-${i}`} aria-controls={`tabpanel-${i}`} />
                ))}
            </Tabs>
            {tabs[value].content}
        </>
    );
};
