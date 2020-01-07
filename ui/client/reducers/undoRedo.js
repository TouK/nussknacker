import * as _ from "lodash"

const emptyHistory = {
  history: {
    past: [],
    future: []
  }
}

const espUndoableConfig = {
  blacklist: [
    "CLEAR_PROCESS", "PROCESS_LOADING", "URL_CHANGED"
  ]
}

export function undoRedoReducer (reducer, config = espUndoableConfig) {
  // eslint-disable-next-line i18next/no-literal-string
  const blacklist = _.concat(["@@INIT"], config.blacklist)

  const undoableFun = (state = emptyHistory, action) => {
    if (_.includes(blacklist, action.type)) {
      return reducer(state, action)
    } else {
      switch (action.type) {
        case "JUMP_TO_STATE":
          switch (action.direction) {
            case "PAST": {
              const newPast = state.history.past.slice(0, action.index + 1)
              const futurePartFromPast = state.history.past.slice(action.index + 1)
              const stateBasedOnPast = _.reduce(_.concat({}, newPast), reducer)
              return {
                ...stateBasedOnPast,
                history: {
                  past: newPast,
                  future: _.concat(futurePartFromPast, state.history.future)
                }
              }
            }
            case "FUTURE": {
              const pastPartFromFuture = state.history.future.slice(0, action.index + 1)
              const newFuture = state.history.future.slice(action.index + 1)
              const newPast = _.concat(state.history.past, pastPartFromFuture)
              const stateBasedOnPast = _.reduce(_.concat({}, newPast), reducer)
              return {
                ...stateBasedOnPast,
                history: {
                  past: newPast,
                  future: newFuture
                }
              }
            }
          }
        case "UNDO":
          const nextIndex = state.history.past.length - 2
          return undoableFun(state, {
            type: "JUMP_TO_STATE",
            index: nextIndex < 0 ? 1 : nextIndex,
            direction: "PAST"
          })
        case "REDO":
          return undoableFun(state, {type: "JUMP_TO_STATE", index: 0, direction: "FUTURE"})
        case "CLEAR":
          return {
            ...state,
            ...emptyHistory
          }
        default: {
          const newState = reducer(state, action)
          return _.isEqual(newState, state) ? state : {
            ...newState,
            history: {
              ...state.history,
              past: _.concat(state.history.past, action),
              future: []
            }
          }
        }
      }
    }
  }
  return undoableFun
}
