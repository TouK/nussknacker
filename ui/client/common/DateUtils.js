
export default {


  format(dateTimeString) {
    //TODO: something better?
    return dateTimeString.replace("T", " | ").substring(0, 18)
  }

}