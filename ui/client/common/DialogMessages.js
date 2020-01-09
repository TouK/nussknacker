
class DialogMessages {
  unsavedProcessChanges = () => {
    return "There are some unsaved process changes. Do you want to discard unsaved changes?"
  }
  deploy = (processId) => {
    return `Are you sure you want to deploy ${processId}?`
  }
  migrate = (processId, environmentId) => {
    return `Are you sure you want to migrate ${processId} to ${environmentId}?`
  }

  stop = (processId) => {
    return `Are you sure you want to stop ${processId}?`
  }

  archiveProcess = (processId) => {
      return `Are you sure you want to archive ${processId}?`
  }

  deleteComment = () => {
    return "Are you sure you want to delete comment?"
  }

  cantArchiveRunningProcess = () => {
    return "You can't archive running process! Stop it first and then click 'archive' button again."
  }

}
//TODO this pattern is not necessary, just export every public function as in actions.js
export default new DialogMessages()
