import { Box, styled } from "@mui/material";
import { mapValues } from "lodash";
import React from "react";
import Inspector, { chromeDark, InspectorNodeParams, ObjectLabel, ObjectName } from "react-inspector";
import { Context } from "../../../../common/TestResultUtils";

export function ContextTree({ context, oldFields = [] }: { context: Context; oldFields?: string[] }): JSX.Element {
    const data = mapValues(context?.variables, (v) => v?.pretty);
    const newFields = Object.keys(data).filter((k) => !oldFields.includes(k));
    return (
        <Box
            sx={(theme) => ({
                "--objectNameColor": theme.palette.primary.main,
                zoom: 1.5,
                background: "rgba(0,0,0,0.5)",
                "&> ol > li": {
                    "&> div:first-of-type": {
                        display: "none",
                    },
                    "&> ol:first-of-type": {
                        paddingLeft: "6px !important",
                    },
                },
            })}
        >
            <Inspector
                theme={{
                    ...chromeDark,
                    BASE_BACKGROUND_COLOR: "transparent",
                    OBJECT_NAME_COLOR: "var(--objectNameColor, inherit)",
                }}
                expandLevel={newFields.length ? 1 : 2}
                expandPaths={["$"].concat(newFields.map((k) => `$.${k}`))}
                data={data}
                sortObjectKeys
                nodeRenderer={getNodeRenderer(oldFields)}
            />
        </Box>
    );
}

const ValueWrapper = styled("span")({
    "--objectNameColor": "lime",
});

const getNodeRenderer = (oldFields: string[]) => {
    return function renderer({ name, data, isNonenumerable, expanded, depth }: InspectorNodeParams) {
        const Wrapper = depth !== 1 || oldFields.includes(name) ? React.Fragment : ValueWrapper;
        return (
            <Wrapper>
                {expanded ? <ObjectName name={name} /> : <ObjectLabel name={name} data={data} isNonenumerable={isNonenumerable} />}
            </Wrapper>
        );
    };
};
