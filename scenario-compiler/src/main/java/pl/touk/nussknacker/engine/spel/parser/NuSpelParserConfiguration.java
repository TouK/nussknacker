package pl.touk.nussknacker.engine.spel.parser;

import org.springframework.expression.spel.SpelCompilerMode;
import org.springframework.expression.spel.SpelParserConfiguration;

import javax.annotation.Nullable;

/**
 * {@link SpelParserConfiguration} with relaxed maximumExpressionLength limit
 */
public class NuSpelParserConfiguration extends SpelParserConfiguration {
    public NuSpelParserConfiguration() {
        this(null, null);
    }

    public NuSpelParserConfiguration(@Nullable SpelCompilerMode compilerMode) {
        this(compilerMode, null);
    }

    public NuSpelParserConfiguration(@Nullable SpelCompilerMode compilerMode, @Nullable ClassLoader compilerClassLoader) {
        // maximumExpressionLength in default configuration is limited to 10_000 (DEFAULT_MAX_EXPRESSION_LENGTH)
        super(
                compilerMode,
                compilerClassLoader,
                /* autoGrowNullReferences */ false,
                /* autoGrowCollections */ false,
                /* maximumAutoGrowSize */ Integer.MAX_VALUE,
                /* maximumExpressionLength */ Integer.MAX_VALUE
        );
    }
}
