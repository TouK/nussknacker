package pl.touk.nussknacker.engine.javaexample;

public class Client {

    private String id;
    private String name;
    private String cardNumber;

    public Client(String id, String name, String cardNumber) {
        this.id = id;
        this.name = name;
        this.cardNumber = cardNumber;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCardNumber() {
        return cardNumber;
    }
}
