package pl.touk.nussknacker.engine.types;

import pl.touk.nussknacker.engine.api.Documentation;
import pl.touk.nussknacker.engine.api.ParamName;

public class JavaSampleDocumentedClass {
    static final String bazDocs = "This is sample documentation for baz method";
    static final String field2Docs = "This is sample documentation for field2 field";
    static final String quxDocs = "This is sample documentation for qux method";

    public long foo(String fooParam1) {
        return 0L;
    }

    public String bar(@ParamName("barparam1") long barparam1) {
        return "";
    }

    @Documentation(description = bazDocs)
    public long baz(@ParamName("bazparam1") String bazparam1, @ParamName("bazparam2") int bazparam2) {
        return 0L;
    }

    @Documentation(description = quxDocs)
    public long qux(String quxParam1) {
        return 0L;
    }

    public long field1 = 123L;

    @Documentation(description = field2Docs)
    public long field2 = 123L;

}
