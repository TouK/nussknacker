import type { Action } from "redux";
import type { StateWithHistory } from "redux-undo";
import { v4 as uuid4 } from "uuid";

class BatchGroupBy {
    private group: string = null;
    private timeout: number;

    startOrExtend = (timeout = 500): void => {
        window.clearTimeout(this.timeout);
        if (!this.group) {
            this.group = uuid4();
        }
        this.timeout = window.setTimeout(() => {
            this.group = null;
        }, timeout);
    };

    init = <S, A extends Action>(action: A, currentState: S, previousHistory: StateWithHistory<S>) => this.group;
}

export const batchGroupBy = new BatchGroupBy();
