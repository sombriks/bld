/*
 * Copyright 2001-2023 Geert Bevin (gbevin[remove] at uwyn dot com)
 * Licensed under the Apache License, Version 2.0 (the "License")
 */
package rife;

import rife.bld.BuildCommand;
import rife.bld.Cli;
import rife.bld.extension.ZipOperation;
import rife.bld.operations.*;
import rife.bld.publish.*;
import rife.tools.DirBuilder;
import rife.tools.FileUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.jar.Attributes;
import java.util.regex.Pattern;

import static rife.bld.dependencies.Repository.*;
import static rife.bld.dependencies.Scope.*;
import static rife.bld.operations.TemplateType.*;
import static rife.tools.FileUtils.path;

public class BldBuild extends AbstractRife2Build {
    public BldBuild()
    throws Exception {
        name = "bld";
        mainClass = "rife.bld.Cli";
        version = version(FileUtils.readString(new File(srcMainResourcesDirectory(), "BLD_VERSION")));

        repositories = List.of(MAVEN_CENTRAL, RIFE2_RELEASES);
        scope(test)
            .include(dependency("org.json", "json", version(20230618)));

        var core_directory = new File(workDirectory(), "core");
        var core_src_directory = new File(core_directory, "src");
        var core_src_main_directory = new File(core_src_directory, "main");
        var core_src_main_java_directory = new File(core_src_main_directory, "java");
        var core_src_main_resources_directory = new File(core_src_main_directory, "resources");
        var core_src_test_directory = new File(core_src_directory, "test");
        var core_src_test_java_directory = new File(core_src_test_directory, "java");
        var core_src_test_resources_directory = new File(core_src_test_directory, "resources");
        var core_src_main_resources_templates_directory = new File(core_src_main_resources_directory, "templates");

        antlr4Operation
            .sourceDirectories(List.of(new File(core_src_main_directory, "antlr")))
            .outputDirectory(new File(buildDirectory(), "generated/rife/template/antlr"));

        precompileOperation()
            .sourceDirectories(core_src_main_resources_templates_directory)
            .templateTypes(HTML, XML, SQL, TXT, JSON);

        compileOperation()
            .mainSourceDirectories(antlr4Operation.outputDirectory(), core_src_main_java_directory)
            .testSourceDirectories(core_src_test_java_directory)
            .compileOptions()
                .debuggingInfo(JavacOptions.DebuggingInfo.ALL);

        jarOperation()
            .sourceDirectories(core_src_main_resources_directory)
            .excluded(Pattern.compile("^\\Q" + core_src_main_resources_templates_directory.getAbsolutePath() + "\\E.*"))
            .manifestAttribute(Attributes.Name.MAIN_CLASS, mainClass());

        zipBldOperation
            .destinationDirectory(buildDistDirectory())
            .destinationFileName("bld-" + version() + ".zip");

        testsBadgeOperation
            .classpath(core_src_main_resources_directory.getAbsolutePath())
            .classpath(core_src_test_resources_directory.getAbsolutePath());

        javadocOperation()
            .sourceFiles(FileUtils.getJavaFileList(core_src_main_java_directory))
            .javadocOptions()
                .docTitle("<a href=\"https://rife2.com/bld\">bld</a> " + version())
                .overview(new File(srcMainJavaDirectory(), "overview.html"));

        publishOperation()
            .repository(version.isSnapshot() ? repository("rife2-snapshots") : repository("rife2-releases"))
            .repository(version.isSnapshot() ? repository("sonatype-snapshots") : repository("sonatype-releases"))
            .info(new PublishInfo()
                .groupId("com.uwyn.rife2")
                .artifactId("bld")
                .name("bld")
                .description("Pure java build tool for developers who don't like dealing with build tools.")
                .url("https://github.com/rife2/bld")
                .developer(new PublishDeveloper()
                    .id("gbevin")
                    .name("Geert Bevin")
                    .email("gbevin@uwyn.com")
                    .url("https://github.com/gbevin"))
                .license(new PublishLicense()
                    .name("The Apache License, Version 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0.txt"))
                .scm(new PublishScm()
                    .connection("scm:git:https://github.com/rife2/bld.git")
                    .developerConnection("scm:git:git@github.com:rife2/bld.git")
                    .url("https://github.com/rife2/bld"))
                .signKey(property("sign.key"))
                .signPassphrase(property("sign.passphrase")))
            .artifacts(
                new PublishArtifact(zipBldOperation.destinationFile(), "", "zip"));
    }


    final ZipOperation zipBldOperation = new ZipOperation();
    @BuildCommand(value = "zip-bld", summary = "Creates the bld zip archive")
    public void zipBld()
    throws Exception {
        jar();
        var tmp = Files.createTempDirectory("bld").toFile();
        try {
            new RunOperation()
                .workDirectory(tmp)
                .mainClass(Cli.class.getName())
                .classpath(runClasspath())
                .runOptions("upgrade")
                .outputProcessor(s -> true)
                .execute();

            new DirBuilder(tmp, t -> {
                t.dir("bld", b -> {
                    b.dir("bin", i -> {
                        i.file("bld", f -> {
                            f.copy(path(srcMainDirectory(), "bld", "bld"));
                            f.perms(0755);
                        });
                        i.file("bld.bat", f -> {
                            f.copy(path(srcMainDirectory(), "bld", "bld.bat"));
                            f.perms(0755);
                        });
                    });
                    b.dir("lib", l -> {
                        l.file("bld-wrapper.jar", f -> f.move(path(tmp, "lib", "bld", "bld-wrapper.jar")));
                    });
                });
                t.dir("lib", l -> l.delete());
            });

            zipBldOperation
                .sourceDirectories(tmp)
                .execute();
        } finally {
            FileUtils.deleteDirectory(tmp);
        }
    }

    @BuildCommand(summary = "Creates all the distribution artifacts")
    public void all()
    throws Exception {
        jar();
        jarSources();
        jarJavadoc();
        zipBld();
    }

    public void publish()
    throws Exception {
        all();
        super.publish();
    }

    public void publishLocal()
    throws Exception {
        all();
        super.publishLocal();
    }

    public static void main(String[] args)
    throws Exception {
        new BldBuild().start(args);
    }
}