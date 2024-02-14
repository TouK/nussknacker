import React, { useCallback } from "react";
import { createSelector } from "reselect";
import { useSelector } from "react-redux";
import CommentContent from "./CommentContent";
import Date from "../common/Date";
import { ProcessCommentsList, RemoveButton } from "./StyledComment";
import { getFeatureSettings, getLoggedUser } from "../../reducers/selectors/settings";
import { ListSeparator } from "../common/ListSeparator";
import { Typography } from "@mui/material";

const getComments = (state) => state.processActivity?.comments || [];
const getCommentSettings = createSelector(getFeatureSettings, (f) => f.commentSettings || {});

interface CommentsListProps {
    deleteComment: (comment) => void;
}

export default function CommentsList({ deleteComment }: CommentsListProps) {
    const loggedUser = useSelector(getLoggedUser);
    const comments = useSelector(getComments);
    const commentSettings = useSelector(getCommentSettings);

    const isLastComment = useCallback((index) => index + 1 === comments.length, [comments.length]);

    return (
        <ProcessCommentsList>
            {comments.map((comment, index) => (
                <div key={comment.id}>
                    <div style={{ width: "100%" }}>
                        <Date date={comment.createDate} />
                        <Typography
                            component={"span"}
                            variant={"overline"}
                            ml={0.5}
                            sx={(theme) => ({ color: theme.custom.colors.silverChalice })}
                        >{`| v${comment.processVersionId} | ${comment.user}`}</Typography>
                        {comment.user != loggedUser.id ? null : <RemoveButton onClick={() => deleteComment(comment)} />}
                    </div>
                    <CommentContent content={comment.content} commentSettings={commentSettings} />
                    {!isLastComment(index) && <ListSeparator />}
                </div>
            ))}
        </ProcessCommentsList>
    );
}
