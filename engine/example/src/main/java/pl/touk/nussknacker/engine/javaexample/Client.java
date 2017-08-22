package pl.touk.nussknacker.engine.javaexample;

//TODO We use public fields here to have the same tests for scala and java code in ExampleItTests
//Right now our validation does not treat getters as normal public field access as SPEL does (eg getName() as '.name' in SPEL)
public class Client {
    public String id;
    public String name;
    public String cardNumber;

    public Client(String id, String name, String cardNumber) {
        this.id = id;
        this.name = name;
        this.cardNumber = cardNumber;
    }

}
