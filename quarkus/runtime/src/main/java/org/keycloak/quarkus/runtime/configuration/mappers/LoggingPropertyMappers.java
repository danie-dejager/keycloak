package org.keycloak.quarkus.runtime.configuration.mappers;

import static org.keycloak.config.LoggingOptions.DEFAULT_LOG_FORMAT;
import static org.keycloak.config.LoggingOptions.LOG_CONSOLE_ENABLED;
import static org.keycloak.config.LoggingOptions.LOG_FILE_ENABLED;
import static org.keycloak.config.LoggingOptions.LOG_SYSLOG_ENABLED;
import static org.keycloak.quarkus.runtime.configuration.Configuration.isSet;
import static org.keycloak.quarkus.runtime.configuration.Configuration.isTrue;
import static org.keycloak.quarkus.runtime.configuration.mappers.PropertyMapper.fromOption;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.stream.Stream;

import io.quarkus.runtime.configuration.MemorySizeConverter;
import org.jboss.logmanager.LogContext;
import org.keycloak.config.LoggingOptions;
import org.keycloak.config.Option;
import org.keycloak.quarkus.runtime.Messages;
import org.keycloak.quarkus.runtime.cli.PropertyException;
import org.keycloak.quarkus.runtime.configuration.Configuration;

import io.smallrye.config.ConfigSourceInterceptorContext;

public final class LoggingPropertyMappers {

    private static final String CONSOLE_ENABLED_MSG = "Console log handler is activated";
    private static final String FILE_ENABLED_MSG = "File log handler is activated";
    private static final String SYSLOG_ENABLED_MSG = "Syslog is activated";
    private static final String DEFAULT_ROOT_LOG_LEVEL = toLevel(LoggingOptions.LOG_LEVEL.getDefaultValue().orElseThrow().get(0)).getName();

    private static List<CategoryLevel> rootLogLevels;

    private LoggingPropertyMappers() {
    }

    public static PropertyMapper<?>[] getMappers() {
        rootLogLevels = null; // reset the cached root log level and categories
        PropertyMapper<?>[] defaultMappers = new PropertyMapper[]{
                fromOption(LoggingOptions.LOG)
                        .paramLabel("<handler>")
                        .build(),
                fromOption(LoggingOptions.LOG_ASYNC)
                        .build(),
                // Console
                fromOption(LoggingOptions.LOG_CONSOLE_OUTPUT)
                        .isEnabled(LoggingPropertyMappers::isConsoleEnabled, CONSOLE_ENABLED_MSG)
                        .to("quarkus.log.console.json.enabled")
                        .paramLabel("output")
                        .transformer(LoggingPropertyMappers::resolveLogOutput)
                        .build(),
                fromOption(LoggingOptions.LOG_CONSOLE_LEVEL)
                        .isEnabled(LoggingPropertyMappers::isConsoleEnabled, CONSOLE_ENABLED_MSG)
                        .to("quarkus.log.console.level")
                        .paramLabel("level")
                        .transformer(LoggingPropertyMappers::upperCase)
                        .build(),
                fromOption(LoggingOptions.LOG_CONSOLE_FORMAT)
                        .isEnabled(LoggingPropertyMappers::isConsoleEnabled, CONSOLE_ENABLED_MSG)
                        .to("quarkus.log.console.format")
                        .paramLabel("format")
                        .transformer((value, ctx) -> addTracingInfo(value, LoggingOptions.LOG_CONSOLE_INCLUDE_TRACE))
                        .build(),
                fromOption(LoggingOptions.LOG_CONSOLE_JSON_FORMAT)
                        .isEnabled(LoggingPropertyMappers::isConsoleJsonEnabled, "%s and output is set to 'json'".formatted(CONSOLE_ENABLED_MSG))
                        .to("quarkus.log.console.json.log-format")
                        .paramLabel("format")
                        .build(),
                fromOption(LoggingOptions.LOG_CONSOLE_INCLUDE_TRACE)
                        .isEnabled(() -> LoggingPropertyMappers.isConsoleEnabled() && TracingPropertyMappers.isTracingEnabled(),
                                "Console log handler and Tracing is activated")
                        .build(),
                fromOption(LoggingOptions.LOG_CONSOLE_COLOR)
                        .isEnabled(LoggingPropertyMappers::isConsoleEnabled, CONSOLE_ENABLED_MSG)
                        .to("quarkus.log.console.color")
                        .build(),
                fromOption(LoggingOptions.LOG_CONSOLE_ENABLED)
                        .mapFrom(LoggingOptions.LOG, LoggingPropertyMappers.resolveLogHandler(LoggingOptions.DEFAULT_LOG_HANDLER.name()))
                        .to("quarkus.log.console.enable")
                        .build(),
                // Console async
                fromOption(LoggingOptions.LOG_CONSOLE_ASYNC)
                        .mapFrom(LoggingOptions.LOG_ASYNC)
                        .isEnabled(LoggingPropertyMappers::isConsoleEnabled, CONSOLE_ENABLED_MSG)
                        .to("quarkus.log.console.async")
                        .build(),
                fromOption(LoggingOptions.LOG_CONSOLE_ASYNC_QUEUE_LENGTH)
                        .isEnabled(LoggingPropertyMappers::isConsoleAsyncEnabled, "%s and asynchronous logging is enabled".formatted(CONSOLE_ENABLED_MSG))
                        .to("quarkus.log.console.async.queue-length")
                        .paramLabel("queue-length")
                        .build(),
                // File
                fromOption(LoggingOptions.LOG_FILE_ENABLED)
                        .mapFrom(LoggingOptions.LOG, LoggingPropertyMappers.resolveLogHandler("file"))
                        .to("quarkus.log.file.enable")
                        .build(),
                fromOption(LoggingOptions.LOG_FILE)
                        .isEnabled(LoggingPropertyMappers::isFileEnabled, FILE_ENABLED_MSG)
                        .to("quarkus.log.file.path")
                        .paramLabel("file")
                        .transformer(LoggingPropertyMappers::resolveFileLogLocation)
                        .build(),
                fromOption(LoggingOptions.LOG_FILE_LEVEL)
                        .isEnabled(LoggingPropertyMappers::isFileEnabled, FILE_ENABLED_MSG)
                        .to("quarkus.log.file.level")
                        .paramLabel("level")
                        .transformer(LoggingPropertyMappers::upperCase)
                        .build(),
                fromOption(LoggingOptions.LOG_FILE_FORMAT)
                        .isEnabled(LoggingPropertyMappers::isFileEnabled, FILE_ENABLED_MSG)
                        .to("quarkus.log.file.format")
                        .paramLabel("format")
                        .transformer((value, ctx) -> addTracingInfo(value, LoggingOptions.LOG_FILE_INCLUDE_TRACE))
                        .build(),
                fromOption(LoggingOptions.LOG_FILE_JSON_FORMAT)
                        .isEnabled(LoggingPropertyMappers::isFileJsonEnabled, FILE_ENABLED_MSG + " and output is set to 'json'")
                        .to("quarkus.log.file.json.log-format")
                        .paramLabel("format")
                        .build(),
                fromOption(LoggingOptions.LOG_FILE_INCLUDE_TRACE)
                        .isEnabled(() -> LoggingPropertyMappers.isFileEnabled() && TracingPropertyMappers.isTracingEnabled(),
                                "File log handler and Tracing is activated")
                        .build(),
                fromOption(LoggingOptions.LOG_FILE_OUTPUT)
                        .isEnabled(LoggingPropertyMappers::isFileEnabled, FILE_ENABLED_MSG)
                        .to("quarkus.log.file.json.enabled")
                        .paramLabel("output")
                        .transformer(LoggingPropertyMappers::resolveLogOutput)
                        .build(),
                // File async
                fromOption(LoggingOptions.LOG_FILE_ASYNC)
                        .mapFrom(LoggingOptions.LOG_ASYNC)
                        .isEnabled(LoggingPropertyMappers::isFileEnabled, FILE_ENABLED_MSG)
                        .to("quarkus.log.file.async")
                        .build(),
                fromOption(LoggingOptions.LOG_FILE_ASYNC_QUEUE_LENGTH)
                        .isEnabled(LoggingPropertyMappers::isFileAsyncEnabled, "%s and asynchronous logging is enabled".formatted(FILE_ENABLED_MSG))
                        .to("quarkus.log.file.async.queue-length")
                        .paramLabel("queue-length")
                        .build(),
                // Log level
                fromOption(LoggingOptions.LOG_LEVEL)
                        .to("quarkus.log.level")
                        .transformer(LoggingPropertyMappers::resolveRootLogLevel)
                        .validator(LoggingPropertyMappers::validateLogLevel)
                        .paramLabel("category:level")
                        .build(),
                fromOption(LoggingOptions.LOG_LEVEL_CATEGORY)
                        .to("quarkus.log.category.\"<categories>\".level")
                        .validator(LoggingPropertyMappers::validateCategoryLogLevel)
                        .wildcardKeysTransformer(LoggingPropertyMappers::getConfiguredLogCategories)
                        .transformer((v,c) -> v == null ? null : toLevel(v).getName())
                        .wildcardMapFrom(LoggingOptions.LOG_LEVEL, LoggingPropertyMappers::resolveCategoryLogLevelFromParentLogLevelOption) // a fallback to log-level
                        .paramLabel("level")
                        .build(),
                // Syslog
                fromOption(LoggingOptions.LOG_SYSLOG_ENABLED)
                        .mapFrom(LoggingOptions.LOG, LoggingPropertyMappers.resolveLogHandler("syslog"))
                        .to("quarkus.log.syslog.enable")
                        .build(),
                fromOption(LoggingOptions.LOG_SYSLOG_ENDPOINT)
                        .isEnabled(LoggingPropertyMappers::isSyslogEnabled, SYSLOG_ENABLED_MSG)
                        .to("quarkus.log.syslog.endpoint")
                        .paramLabel("host:port")
                        .build(),
                fromOption(LoggingOptions.LOG_SYSLOG_LEVEL)
                        .isEnabled(LoggingPropertyMappers::isSyslogEnabled, SYSLOG_ENABLED_MSG)
                        .to("quarkus.log.syslog.level")
                        .paramLabel("level")
                        .transformer(LoggingPropertyMappers::upperCase)
                        .build(),
                fromOption(LoggingOptions.LOG_SYSLOG_APP_NAME)
                        .isEnabled(LoggingPropertyMappers::isSyslogEnabled, SYSLOG_ENABLED_MSG)
                        .to("quarkus.log.syslog.app-name")
                        .paramLabel("name")
                        .build(),
                fromOption(LoggingOptions.LOG_SYSLOG_TYPE)
                        .isEnabled(LoggingPropertyMappers::isSyslogEnabled, SYSLOG_ENABLED_MSG)
                        .to("quarkus.log.syslog.syslog-type")
                        .paramLabel("type")
                        .build(),
                fromOption(LoggingOptions.LOG_SYSLOG_MAX_LENGTH)
                        .isEnabled(LoggingPropertyMappers::isSyslogEnabled, SYSLOG_ENABLED_MSG)
                        .to("quarkus.log.syslog.max-length")
                        .validator(LoggingPropertyMappers::validateSyslogMaxLength)
                        .paramLabel("max-length")
                        .build(),
                fromOption(LoggingOptions.LOG_SYSLOG_PROTOCOL)
                        .isEnabled(LoggingPropertyMappers::isSyslogEnabled, SYSLOG_ENABLED_MSG)
                        .to("quarkus.log.syslog.protocol")
                        .paramLabel("protocol")
                        .build(),
                fromOption(LoggingOptions.LOG_SYSLOG_FORMAT)
                        .isEnabled(LoggingPropertyMappers::isSyslogEnabled, SYSLOG_ENABLED_MSG)
                        .to("quarkus.log.syslog.format")
                        .paramLabel("format")
                        .transformer((value, ctx) -> addTracingInfo(value, LoggingOptions.LOG_SYSLOG_INCLUDE_TRACE))
                        .build(),
                fromOption(LoggingOptions.LOG_SYSLOG_JSON_FORMAT)
                        .isEnabled(LoggingPropertyMappers::isSyslogJsonEnabled, SYSLOG_ENABLED_MSG + " and output is set to 'json'")
                        .to("quarkus.log.syslog.json.log-format")
                        .paramLabel("format")
                        .build(),
                fromOption(LoggingOptions.LOG_SYSLOG_INCLUDE_TRACE)
                        .isEnabled(() -> LoggingPropertyMappers.isSyslogEnabled() && TracingPropertyMappers.isTracingEnabled(),
                                "Syslog handler and Tracing is activated")
                        .build(),
                fromOption(LoggingOptions.LOG_SYSLOG_OUTPUT)
                        .isEnabled(LoggingPropertyMappers::isSyslogEnabled, SYSLOG_ENABLED_MSG)
                        .to("quarkus.log.syslog.json.enabled")
                        .paramLabel("output")
                        .transformer(LoggingPropertyMappers::resolveLogOutput)
                        .build(),
                // Syslog async
                fromOption(LoggingOptions.LOG_SYSLOG_ASYNC)
                        .mapFrom(LoggingOptions.LOG_ASYNC)
                        .isEnabled(LoggingPropertyMappers::isSyslogEnabled, SYSLOG_ENABLED_MSG)
                        .to("quarkus.log.syslog.async")
                        .build(),
                fromOption(LoggingOptions.LOG_SYSLOG_ASYNC_QUEUE_LENGTH)
                        .isEnabled(LoggingPropertyMappers::isSyslogAsyncEnabled, "%s and asynchronous logging is enabled".formatted(SYSLOG_ENABLED_MSG))
                        .to("quarkus.log.syslog.async.queue-length")
                        .paramLabel("queue-length")
                        .build(),
        };

        return defaultMappers;
    }

    public static boolean isConsoleEnabled() {
        return isHandlerEnabled(LoggingOptions.Handler.console);
    }

    public static boolean isConsoleAsyncEnabled() {
        return isHandlerAsyncEnabled(LoggingOptions.Handler.console);
    }

    public static boolean isConsoleJsonEnabled() {
        return isConsoleEnabled() && isTrue("quarkus.log.console.json.enabled");
    }

    public static boolean isFileEnabled() {
        return isHandlerEnabled(LoggingOptions.Handler.file);
    }

    public static boolean isFileAsyncEnabled() {
        return isHandlerAsyncEnabled(LoggingOptions.Handler.file);
    }

    public static boolean isFileJsonEnabled() {
        return isFileEnabled() && isTrue("quarkus.log.file.json.enabled");
    }

    public static boolean isSyslogEnabled() {
        return isHandlerEnabled(LoggingOptions.Handler.syslog);
    }

    public static boolean isSyslogAsyncEnabled() {
        return isHandlerAsyncEnabled(LoggingOptions.Handler.syslog);
    }

    public static boolean isSyslogJsonEnabled() {
        return isSyslogEnabled() && isTrue("quarkus.log.syslog.json.enabled");
    }

    private static boolean isHandlerEnabled(LoggingOptions.Handler handler) {
        return switch (handler) {
            case console -> isTrue(LOG_CONSOLE_ENABLED);
            case file -> isTrue(LOG_FILE_ENABLED);
            case syslog -> isTrue(LOG_SYSLOG_ENABLED);
        };
    }

    private static boolean isHandlerAsyncEnabled(LoggingOptions.Handler handler) {
        if (!isHandlerEnabled(handler)) {
            return false;
        }
        var property = switch (handler) {
            case console -> LoggingOptions.LOG_CONSOLE_ASYNC;
            case file -> LoggingOptions.LOG_FILE_ASYNC;
            case syslog -> LoggingOptions.LOG_SYSLOG_ASYNC;
        };
        return isSet(property) ? isTrue(property) : isTrue(LoggingOptions.LOG_ASYNC);
    }

    private static BiFunction<String, ConfigSourceInterceptorContext, String> resolveLogHandler(String handler) {
        return (handlers, context) -> {
            String[] logHandlerValues = handlers.split(",");

            return String.valueOf(Stream.of(logHandlerValues).anyMatch(handler::equals));
        };
    }

    private static String resolveFileLogLocation(String value, ConfigSourceInterceptorContext configSourceInterceptorContext) {
        return value.endsWith(File.separator) ? value + LoggingOptions.DEFAULT_LOG_FILENAME : value;
    }

    private static Level toLevel(String categoryLevel) throws IllegalArgumentException {
        return LogContext.getLogContext().getLevelForName(categoryLevel.toUpperCase(Locale.ROOT));
    }

    record CategoryLevel(String category, String levelName) {}

    private static CategoryLevel validateLogLevel(String level) {
        String[] parts = level.split(":");
        String category = null;
        String categoryLevel;

        if (parts.length == 1) {
            categoryLevel = parts[0];
        } else if (parts.length == 2) {
            category = parts[0];
            if (!WildcardPropertyMapper.isValidWildcardValue(category)) {
                throw new PropertyException("logging category '%s' is not valid".formatted(category));
            }
            categoryLevel = parts[1];
        } else {
            throw new PropertyException(Messages.invalidLogCategoryFormat(level));
        }

        try {
            Level levelType = toLevel(categoryLevel);
            return new CategoryLevel(category, levelType.getName());
        } catch (IllegalArgumentException iae) {
            throw new PropertyException(Messages.invalidLogCategoryFormat(level));
        }
    }

    private static String resolveRootLogLevel(String value, ConfigSourceInterceptorContext configSourceInterceptorContext) {
        for (CategoryLevel categoryLevel : parseRootLogLevel(value)) {
            if (categoryLevel.category == null) {
                return categoryLevel.levelName;
            }
        }
        return DEFAULT_ROOT_LOG_LEVEL; // defaults are not resolved in the mapper if transformer is present, so doing it explicitly here
    }

    private static Set<String> getConfiguredLogCategories(String value, Set<String> categories) {
        for (CategoryLevel categoryLevel : parseRootLogLevel(value)) {
            if (categoryLevel.category != null) {
                categories.add(categoryLevel.category);
            }
        }
        return categories;
    }

    private static void validateCategoryLogLevel(String logLevel) {
        try {
            toLevel(logLevel);
        } catch (IllegalArgumentException iae) {
            throw new PropertyException(Messages.invalidLogLevel(logLevel));
        }
    }

    private static String resolveCategoryLogLevelFromParentLogLevelOption(String category, String parentLogLevelValue, ConfigSourceInterceptorContext context) {
        for (CategoryLevel categoryLevel : parseRootLogLevel(parentLogLevelValue)) {
            if (category.equals(categoryLevel.category)) {
                return categoryLevel.levelName;
            }
        }
        return null;
    }

    private static List<CategoryLevel> parseRootLogLevel(String values) {
        if (rootLogLevels == null) {
            var value = values != null ? values : Configuration.getConfigValue(LoggingOptions.LOG_LEVEL).getValue();
            if (value == null) {
                return List.of(); // if no value is present, we do not cache the result
            }

            rootLogLevels = Stream.of(value.split(","))
                    .map(LoggingPropertyMappers::validateLogLevel)
                    .toList();
        }
        return rootLogLevels;
    }

    private static String resolveLogOutput(String value, ConfigSourceInterceptorContext context) {
        boolean isDefault = LoggingOptions.DEFAULT_CONSOLE_OUTPUT.name().toLowerCase(Locale.ROOT).equals(value);
        return Boolean.valueOf(!isDefault).toString();
    }

    /**
     * Add tracing info to the log if the format is not explicitly set, and tracing and {@code includeTraceOption} options are enabled
     */
    private static String addTracingInfo(String value, Option<Boolean> includeTraceOption) {
        var isTracingEnabled = TracingPropertyMappers.isTracingEnabled();
        var includeTrace = isTrue(includeTraceOption);
        var isChangedLogFormat = !DEFAULT_LOG_FORMAT.equals(value);

        if (!isTracingEnabled || !includeTrace || isChangedLogFormat) {
            return value;
        }

        return LoggingOptions.DEFAULT_LOG_TRACING_FORMAT;
    }

    private static String upperCase(String value, ConfigSourceInterceptorContext context) {
        return value.toUpperCase(Locale.ROOT);
    }

    private static void validateSyslogMaxLength(String value) {
        var converter = new MemorySizeConverter();
        try {
            converter.convert(value);
        } catch (IllegalArgumentException e) {
            throw new PropertyException(String.format("Invalid value for option '--log-syslog-max-length': %s", e.getMessage()));
        }
    }
}
