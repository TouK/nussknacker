import React, { useCallback, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { addComment, deleteComment } from "../../actions/nk";
import { getProcessId, getProcessVersionId } from "../../reducers/selectors/graph";
import CommentInput from "./CommentInput";
import { useWindows } from "../../windowManager";
import * as DialogMessages from "../../common/DialogMessages";
import { getCapabilities } from "../../reducers/selectors/other";
import { AddCommentPanel, CommentButton, ProcessCommentsWrapper } from "./StyledComment";
import CommentsList from "./CommentsList";

function ProcessComments(): JSX.Element {
    const [comment, setComment] = useState("");
    const [pending, setPending] = useState(false);
    const dispatch = useDispatch();
    const { confirm } = useWindows();

    const processId = useSelector(getProcessId);
    const processVersionId = useSelector(getProcessVersionId);
    const capabilities = useSelector(getCapabilities);

    const _deleteComment = useCallback(
        (comment) => {
            setPending(true);
            confirm({
                text: DialogMessages.deleteComment(),
                confirmText: "DELETE",
                denyText: "NO",
                onConfirmCallback: async (confirmed) => {
                    if (confirmed) {
                        await dispatch(deleteComment(processId, comment.id));
                    }
                    setPending(false);
                },
            });
        },
        [confirm, dispatch, processId],
    );

    const _addComment = useCallback(async () => {
        setPending(true);
        await dispatch(addComment(processId, processVersionId, comment));
        setPending(false);
        setComment("");
    }, [dispatch, processId, processVersionId, comment]);

    const onInputChange = useCallback((e) => setComment(e.target.value), []);

    return (
        <ProcessCommentsWrapper>
            <CommentsList deleteComment={_deleteComment} />
            {capabilities.write ? (
                <AddCommentPanel>
                    <CommentInput onChange={onInputChange.bind(this)} value={comment} />
                    <CommentButton type="button" onClick={_addComment} disabled={pending || comment == ""}>
                        Add
                    </CommentButton>
                </AddCommentPanel>
            ) : null}
        </ProcessCommentsWrapper>
    );
}

export default ProcessComments;
