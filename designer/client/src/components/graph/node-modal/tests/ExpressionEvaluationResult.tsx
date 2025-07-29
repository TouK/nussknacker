import { ExpandMore } from "@mui/icons-material";
import { Accordion, AccordionSummary, Box, Typography } from "@mui/material";
import { isObject } from "lodash";
import React, { useCallback, useState } from "react";
import type { Styling } from "react-base16-styling/src/types";
import { useTranslation } from "react-i18next";
import { JSONTree } from "react-json-tree";

import { SyntaxHighlighter } from "../../../../common/SyntaxHighlighter";
import { useJsonTreeTheme } from "../../../../containers/theme/useJsonTreeTheme";
import type { Variable } from "../../../../http/resultsWithCountsDto";

interface Props {
    value: Variable;
}

export const ExpressionEvaluationResult = (props: Props) => {
    const { t } = useTranslation();
    const { value } = props;
    const [expanded, setExpanded] = useState(true);
    const { extend, treeStyle } = useJsonTreeTheme();

    const handleExpandedChange = useCallback((event: React.SyntheticEvent, isExpanded: boolean) => {
        setExpanded(isExpanded);
    }, []);

    const dataToShow = value.pretty || value.original;

    return (
        <Accordion className={"expressionEvaluationResult"} expanded={expanded} disableGutters onChange={handleExpandedChange}>
            <AccordionSummary expandIcon={<ExpandMore />}>
                <Typography variant={"body2"}>{t("expressionEvaluationResult", "Expression Evaluation result")}</Typography>
            </AccordionSummary>
            <>
                {isObject(dataToShow) ? (
                    <JSONTree
                        hideRoot
                        sortObjectKeys
                        data={dataToShow}
                        theme={{
                            extend,
                            tree: {
                                ...treeStyle,
                                lineHeight: "19px",
                                margin: 0,
                                padding: ".25em .5em",
                            },
                            value: ({ style }: Styling) => ({
                                style: {
                                    ...style,
                                    paddingTop: 0,
                                },
                            }),
                        }}
                    />
                ) : (
                    <Box bgcolor={(theme) => theme.palette.custom.codeEditor.readonly.backgroundColor} p={1}>
                        <SyntaxHighlighter
                            language={"plain_text"}
                            customStyle={{ border: 0, backgroundColor: "inherit" }}
                            staticHighlightOptions={{ showGutter: false }}
                        >
                            {JSON.stringify(dataToShow, null, 2)}
                        </SyntaxHighlighter>
                    </Box>
                )}
            </>
        </Accordion>
    );
};
