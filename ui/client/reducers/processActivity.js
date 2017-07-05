const emptyProcessActivity = {
  comments: [],
  attachments: []
};

export function reducer(state = emptyProcessActivity, action) {
  switch (action.type) {
    case "DISPLAY_PROCESS_ACTIVITY": {
      return {
        ...state,
        comments: action.comments,
        attachments: action.attachments
      };
    }
    default:
      return state;
  }
}
