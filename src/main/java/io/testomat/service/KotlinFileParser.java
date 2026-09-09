package io.testomat.service;

import io.testomat.model.ParsedKtFile;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation;
import org.jetbrains.kotlin.cli.common.messages.MessageCollector;
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.cli.jvm.config.JvmContentRootsKt;
import org.jetbrains.kotlin.com.intellij.openapi.Disposable;
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer;
import org.jetbrains.kotlin.config.ApiVersion;
import org.jetbrains.kotlin.config.CommonConfigurationKeys;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.config.LanguageVersion;
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtPsiFactory;

@UtilityClass
public class KotlinFileParser {

    private static Disposable disposable;
    private static KotlinCoreEnvironment environment;
    private static KtPsiFactory psiFactory;
    private static CompilerConfiguration configuration;

    private static volatile boolean initialized = false;

    private static synchronized void init() {
        if (initialized) {
            return;
        }

        disposable = Disposer.newDisposable();

        configuration = new CompilerConfiguration();
        configuration.put(CommonConfigurationKeys.MODULE_NAME, "test-module");
        configuration.put(
                CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS,
            new LanguageVersionSettingsImpl(
                LanguageVersion.LATEST_STABLE,
                ApiVersion.LATEST_STABLE
            )
        );

        configuration.put(
                CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY,
            new MessageCollector() {
                @Override
                public void report(@NotNull CompilerMessageSeverity severity,
                        @NotNull String message,
                        @Nullable CompilerMessageSourceLocation location) {

                }

                @Override
                public void clear() {

                }

                @Override
                public boolean hasErrors() {
                    return false;
                }
            }
        );

        File stdlib = new File("libs/kotlin-stdlib.jar");
        if (stdlib.exists()) {
            JvmContentRootsKt.addJvmClasspathRoot(configuration, stdlib);
        }

        File junitApi = new File("libs/junit-jupiter-api.jar");
        if (junitApi.exists()) {
            JvmContentRootsKt.addJvmClasspathRoot(configuration, junitApi);
        }

        environment = KotlinCoreEnvironment.createForProduction(
            disposable,
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        );

        psiFactory = new KtPsiFactory(environment.getProject(), false);

        initialized = true;
    }

    @SneakyThrows
    public static ParsedKtFile parseFile(Path path) {
        init();

        String content = Files.readString(path);
        String fileName = path.getFileName().toString();

        KtFile ktFile = psiFactory.createFile(fileName, content);

        return new ParsedKtFile(ktFile, path);
    }

    public static void shutdown() {
        if (disposable != null) {
            Disposer.dispose(disposable);
            initialized = false;
        }
    }
}
