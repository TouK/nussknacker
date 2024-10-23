import React, { useRef, useState } from "react";
import CommentContent from "../../../comment/CommentContent";
import { ActionMetadata, ActivityComment } from "../types";
import { useSelector } from "react-redux";
import { createSelector } from "reselect";
import { getFeatureSettings } from "../../../../reducers/selectors/settings";
import { Box } from "@mui/material";
import { StyledActionIcon } from "./StyledActionIcon";

const getCommentSettings = createSelector(getFeatureSettings, (f) => f.commentSettings || {});

const CommentActivity = ({ activityAction }: { activityAction: ActionMetadata }) => {
    switch (activityAction.id) {
        case "delete_comment": {
            return (
                <StyledActionIcon
                    data-testid={`delete-comment-icon`}
                    onClick={() => console.log("works")}
                    key={activityAction.id}
                    src={activityAction.icon}
                    title={activityAction.displayableName}
                />
            );
        }
        case "edit_comment": {
            return (
                <StyledActionIcon
                    data-testid={`delete-comment-icon`}
                    onClick={() => console.log("works")}
                    key={activityAction.id}
                    src={activityAction.icon}
                    title={activityAction.displayableName}
                />
            );
        }
    }
};

interface Props {
    comment: ActivityComment;
    searchQuery: string;
    activityActions: ActionMetadata[];
}

export const ActivityItemComment = ({ comment, searchQuery, activityActions }: Props) => {
    const commentSettings = useSelector(getCommentSettings);
    const [isMultiline, setIsMultiline] = useState(false);
    return (
        <Box
            ref={(ref: HTMLDivElement) => {
                console.log(ref);
                if (ref?.clientHeight > 20) {
                    setIsMultiline(true);
                }
            }}
            display="flex"
            alignItems="flex-start"
            justifyContent="space-between"
        >
            <CommentContent
                content={comment.content.value}
                commentSettings={commentSettings}
                searchWords={[searchQuery]}
                variant={"overline"}
            />
            <Box
                display={"flex"}
                alignItems={"flex-end"}
                marginLeft={"auto"}
                flexBasis={"10%"}
                flexDirection={isMultiline ? "column-reverse" : "row"}
            >
                {activityActions.map((activityAction) => (
                    <CommentActivity key={activityAction.id} activityAction={activityAction} />
                ))}
            </Box>
        </Box>
    );
};
