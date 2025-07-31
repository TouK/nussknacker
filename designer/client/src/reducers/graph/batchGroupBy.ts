import type { Action } from "redux";
import type { StateWithHistory } from "redux-undo";
import { v4 as uuid4 } from "uuid";

class BatchGroupBy {
    private group: string = null;

    startOrContinue = (group = uuid4()): string => {
        if (!this.group) {
            this.group = group;
        }
        return group;
    };

    end = (group = this.group): void => {
        if (this.group === group) {
            this.group = null;
        }
    };

    init = <S, A extends Action>(action: A, currentState: S, previousHistory: StateWithHistory<S>) => this.group;
}

export const batchGroupBy = new BatchGroupBy();
