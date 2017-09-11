package pl.touk.nussknacker.engine.javaexample;

import pl.touk.nussknacker.engine.api.MethodToInvoke;
import pl.touk.nussknacker.engine.api.ParamName;
import pl.touk.nussknacker.engine.api.Service;
import scala.Function0;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.Future$;
import scala.runtime.AbstractFunction0;

import java.util.HashMap;

//TODO make it return java completableFuture instead of scala Future
public class ClientService extends Service {

    @MethodToInvoke
    public Future<Client> invoke(@ParamName("clientId") String clientId, ExecutionContext executor) {
        HashMap<String, Client> map = new HashMap<>();
        map.put("Client1", new Client("Client1", "Alice", "123"));
        map.put("Client2", new Client("Client2", "Bob", "234"));
        map.put("Client3", new Client("Client3", "Charles", "345"));
        map.put("Client4", new Client("Client4", "David", "777"));
        map.put("Client5", new Client("Client5", "Eve", "888"));

        Function0<Client> body = new AbstractFunction0<Client>() {
            @Override
            public Client apply() {
                return map.get(clientId);
            }

        };
        return Future$.MODULE$.apply(body, executor);
    }

}
