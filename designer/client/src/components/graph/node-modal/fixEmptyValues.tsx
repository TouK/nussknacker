import type { WritableDraft } from "immer";

import type { NodeType } from "../../../types/node";

function appendEmptyDescription(draft: WritableDraft<NodeType>) {
    draft.additionalFields ||= {};
    draft.additionalFields.description ||= "";
}

export function fixEmptyValues(draft: WritableDraft<NodeType>) {
    appendEmptyDescription(draft);
}
