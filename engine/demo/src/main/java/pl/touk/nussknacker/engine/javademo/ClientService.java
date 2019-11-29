package pl.touk.nussknacker.engine.javademo;

import pl.touk.nussknacker.engine.api.MethodToInvoke;
import pl.touk.nussknacker.engine.api.ParamName;
import pl.touk.nussknacker.engine.api.Service;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class ClientService extends Service {

    @MethodToInvoke
    public CompletableFuture<Client> invoke(@ParamName("clientId") String clientId, Executor executor) {
        HashMap<String, Client> map = new HashMap<>();
        map.put("Client1", new Client("Client1", "Alice", "123"));
        map.put("Client2", new Client("Client2", "Bob", "234"));
        map.put("Client3", new Client("Client3", "Charles", "345"));
        map.put("Client4", new Client("Client4", "David", "777"));
        map.put("Client5", new Client("Client5", "Eve", "888"));

        return CompletableFuture.supplyAsync(() -> map.get(clientId), executor);
    }

}
