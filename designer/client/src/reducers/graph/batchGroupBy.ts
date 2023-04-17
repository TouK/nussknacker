import {v4 as uuid4} from "uuid"
import {GroupByFunction} from "redux-undo"
import {Action, AnyAction} from "redux"

class BatchGroupBy<S = any, A extends Action = AnyAction> {
  private group: string = null

  startOrContinue = (group = uuid4()): string => {
    if (!this.group) {
      this.group = group
    }
    return group
  }

  end = (group = this.group): void => {
    if (this.group === group) {
      this.group = null
    }
  }

  init = (groupBy?: GroupByFunction<S, A>): GroupByFunction<S, A> => {
    return (...args) => this.group || groupBy?.(...args)
  }
}

export const batchGroupBy = new BatchGroupBy()
