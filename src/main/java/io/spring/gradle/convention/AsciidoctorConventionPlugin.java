/*
 * Copyright 2019-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.spring.gradle.convention;

import java.io.File;
import java.net.URI;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.asciidoctor.gradle.jvm.AbstractAsciidoctorTask;
import org.asciidoctor.gradle.jvm.AsciidoctorJExtension;
import org.asciidoctor.gradle.jvm.AsciidoctorJPlugin;
import org.asciidoctor.gradle.jvm.AsciidoctorTask;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskAction;

/**
 * Conventions that are applied in the presence of the {@link AsciidoctorJPlugin}. When
 * the plugin is applied:
 *
 * <ul>
 * <li>All warnings are made fatal.
 * <li>A task is created to resolve and unzip our documentation resources (CSS and
 * Javascript).
 * <li>For each {@link AsciidoctorTask} (HTML only):
 * <ul>
 * <li>A task is created to sync the documentation resources to its output directory.
 * <li>{@code doctype} {@link AsciidoctorTask#options(Map) option} is configured.
 * <li>{@link AsciidoctorTask#attributes(Map) Attributes} are configured for syntax
 * highlighting, CSS styling, docinfo, etc.
 * </ul>
 * <li>For each {@link AbstractAsciidoctorTask} (HTML and PDF):
 * <ul>
 * <li>{@link AsciidoctorTask#attributes(Map) Attributes} are configured to enable
 * warnings for references to missing attributes, the GitHub tag, the Artifactory repo for
 * the current version, etc.
 * <li>{@link AbstractAsciidoctorTask#baseDirFollowsSourceDir() baseDirFollowsSourceDir()}
 * is enabled.
 * </ul>
 * </ul>
 *
 * @author Andy Wilkinson
 * @author Rob Winch
 */
public class AsciidoctorConventionPlugin implements Plugin<Project> {

	public void apply(Project project) {
		project.getPlugins().withType(AsciidoctorJPlugin.class, (asciidoctorPlugin) -> {
			createDefaultAsciidoctorRepository(project);
			makeAllWarningsFatal(project);
			Sync unzipResources = createUnzipDocumentationResourcesTask(project);
			project.getTasks().withType(AbstractAsciidoctorTask.class, (asciidoctorTask) -> {
				asciidoctorTask.dependsOn(unzipResources);
				configureHtmlOnlyAttributes(project, asciidoctorTask);
				configureExtensions(project, asciidoctorTask);
				configureCommonAttributes(project, asciidoctorTask);
				configureOptions(asciidoctorTask);
				asciidoctorTask.baseDirFollowsSourceFile();
				Sync syncSource = createSyncDocumentationSourceTask(project, asciidoctorTask);
				syncSource.from(unzipResources, (resources) -> resources.into(asciidoctorTask.getOutputDir().getName(), new Action<CopySpec>() {
					@Override
					public void execute(CopySpec copySpec) {
						copySpec.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
					}
				}));

				asciidoctorTask.doFirst(new Action<Task>() {

					@Override
					public void execute(Task task) {
						for (File backendOutputDir : asciidoctorTask.getBackendOutputDirectories()) {
							System.out.println(asciidoctorTask.getSourceDir() + " to outputDir "
									+ backendOutputDir);
							project.copy((spec) -> {
								spec.from(asciidoctorTask.getSourceDir());
								spec.into(backendOutputDir);
								spec.include("css/**", "js/**");
							});
						}
					}
				});
				if (asciidoctorTask instanceof AsciidoctorTask) {
					configureHtmlOnlyAttributes(project, asciidoctorTask);
				}
			});
		});
	}

	private void createDefaultAsciidoctorRepository(Project project) {
//		project.getGradle().afterProject(new Action<Project>() {
//			@Override
//			public void execute(Project project) {
				RepositoryHandler repositories = project.getRepositories();
				if (repositories.isEmpty()) {
					repositories.maven(repo -> {
						repo.setUrl(URI.create("https://repo.spring.io/libs-release"));
					});
				}
//			}
//		});
	}

	private void makeAllWarningsFatal(Project project) {
		project.getExtensions().getByType(AsciidoctorJExtension.class).fatalWarnings(".*");
	}

	private void configureExtensions(Project project, AbstractAsciidoctorTask asciidoctorTask) {
		Configuration extensionsConfiguration = project.getConfigurations().maybeCreate("asciidoctorExtensions");
		extensionsConfiguration.defaultDependencies(new Action<DependencySet>() {
			@Override
			public void execute(DependencySet dependencies) {
				dependencies.add(project.getDependencies().create("io.spring.asciidoctor:spring-asciidoctor-extensions-block-switch:0.3.0.RELEASE"));
			}
		});
		asciidoctorTask.configurations(extensionsConfiguration);
	}

	private Sync createSyncDocumentationSourceTask(Project project, AbstractAsciidoctorTask asciidoctorTask) {
		Sync syncDocumentationSource = project.getTasks()
				.create("syncDocumentationSourceFor" + capitalize(asciidoctorTask.getName()), Sync.class);
		File destinationDir = new File(project.getBuildDir(), "docs/src/" + asciidoctorTask.getName());
		syncDocumentationSource.setDestinationDir(destinationDir);
		syncDocumentationSource.from(asciidoctorTask.getSourceDir().getParent());
		asciidoctorTask.dependsOn(syncDocumentationSource);
		asciidoctorTask.setSourceDir(project.relativePath(new File(syncDocumentationSource.getDestinationDir(), asciidoctorTask.getSourceDir().getName())));
		return syncDocumentationSource;
	}

	private static String capitalize(String value) {
		if (value == null) {
			return null;
		}
		char [] chars = value.toCharArray();
		if (chars.length > 0) {
			chars[0] = Character.toUpperCase(chars[0]);
		}
		return new String(chars);
	}


	private Sync createUnzipDocumentationResourcesTask(Project project) {
		Configuration documentationResources = project.getConfigurations().maybeCreate("documentationResources");
		documentationResources.getDependencies()
				.add(project.getDependencies().create("io.spring.docresources:spring-doc-resources:0.1.3.RELEASE"));
		Sync unzipResources = project.getTasks().create("unzipDocumentationResources",
				Sync.class);
		unzipResources.into(new File(project.getBuildDir(), "docs/resources"));
		documentationResources.getAsFileTree().forEach(file ->
			unzipResources.from(project.zipTree(file))
		);
		return unzipResources;
	}

	private void configureOptions(AbstractAsciidoctorTask asciidoctorTask) {
		asciidoctorTask.options(Collections.singletonMap("doctype", "book"));
	}

	private void configureHtmlOnlyAttributes(Project project, AbstractAsciidoctorTask asciidoctorTask) {
		Map<String, Object> attributes = new HashMap<>();

		attributes.put("source-highlighter", "highlight.js");
		attributes.put("highlightjsdir", "js/highlight");
		attributes.put("highlightjs-theme", "github");
		attributes.put("linkcss", true);
		attributes.put("icons", "font");
		attributes.put("stylesheet", "css/spring.css");
		asciidoctorTask.attributes(attributes);
	}

	private void configureCommonAttributes(Project project, AbstractAsciidoctorTask asciidoctorTask) {
		Map<String, Object> attributes = new HashMap<>();
		attributes.put("attribute-missing", "warn");
		attributes.put("icons", "font");
		attributes.put("idprefix", "");
		attributes.put("idseparator", "-");
		attributes.put("docinfo", "shared");
		attributes.put("sectanchors", "");
		attributes.put("sectnums", "");
		attributes.put("today-year", LocalDate.now().getYear());
		asciidoctorTask.attributes(attributes);
	}

}