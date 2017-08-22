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
        map.put("ClientA", new Client("ClientA", "Alice", "123"));
        map.put("ClientB", new Client("ClientB", "Bob", "234"));
        map.put("ClientC", new Client("ClientC", "Charles", "345"));
        Function0<Client> body = new AbstractFunction0<Client>() {
            @Override
            public Client apply() {
                return map.get(clientId);
            }

        };
        return Future$.MODULE$.apply(body, executor);
    }

}
