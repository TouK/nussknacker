
export default {


  format(dateTimeString) {
    //TODO: cos lepszego
    return dateTimeString.replace("T", " ").substring(0, 16)
  }

}