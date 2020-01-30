package io.spring.gradle.convention

import io.spring.gradle.testkit.junit.TestKit
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import java.io.File

/**
 * @author Rob Winch
 */
internal class AsciidoctorConventionsITest {
    val ASCIIDOCTOR_TASK_NAME = ":asciidoctor"

    @Test
    fun asciidocWhenSimpleThenSuccess() {
        TestKit().use { testKit ->
            val build = testKit
                    .withProjectResource(projectResource("simple"))
                    .withArguments(ASCIIDOCTOR_TASK_NAME)
                    .forwardOutput()
                    .build()
            assertThat(build.task(ASCIIDOCTOR_TASK_NAME)?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun asciidocWhenMultipleBackendsThenSuccess() {
        TestKit().use { testKit ->
            val build = testKit
                    .withProjectResource(projectResource("backends"))
                    .withArguments(ASCIIDOCTOR_TASK_NAME)
                    .forwardOutput()
                    .build()
            assertThat(build.task(ASCIIDOCTOR_TASK_NAME)?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun asciidocWhenBlockSwitchThenSuccess() {
        TestKit().use { testKit ->
            val build = testKit
                    .withProjectResource(projectResource("blockswitch"))
                    .withArguments(ASCIIDOCTOR_TASK_NAME)
                    .forwardOutput()
                    .build()
            assertThat(build.task(ASCIIDOCTOR_TASK_NAME)?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            val indexHtml = File(testKit.projectDir, "build/docs/asciidoc/index.html").readText()
            assertThat(indexHtml).contains(".switch--item.selected")
        }
    }

    @Test
    fun asciidocWhenCustomDocinfoThenSuccess() {
        TestKit().use { testKit ->
            val build = testKit
                    .withProjectResource(projectResource("custom-docinfo"))
                    .withArguments(ASCIIDOCTOR_TASK_NAME)
                    .forwardOutput()
                    .build()
            assertThat(build.task(ASCIIDOCTOR_TASK_NAME)?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            val indexHtml = File(testKit.projectDir, "build/docs/asciidoc/index.html").readText()
            assertThat(indexHtml).contains("Custom Footer")
        }
    }

    @Test
    fun asciidocWhenIncludeParentDirThenSuccess() {
        TestKit().use { testKit ->
            val build = testKit
                    .withProjectResource(projectResource("include-parent"))
                    .withArguments(ASCIIDOCTOR_TASK_NAME)
                    .forwardOutput()
                    .build()
            assertThat(build.task(ASCIIDOCTOR_TASK_NAME)?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            val indexHtml = File(testKit.projectDir, "build/docs/asciidoc/index.html").readText()
            assertThat(indexHtml).contains("Included Content")
        }
    }

    @Test
    fun asciidocWhenCustomCssThenSuccess() {
        TestKit().use { testKit ->
            val build = testKit
                    .withProjectResource(projectResource("custom-css"))
                    .withArguments(ASCIIDOCTOR_TASK_NAME)
                    .forwardOutput()
                    .build()
            assertThat(build.task(ASCIIDOCTOR_TASK_NAME)?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(File(testKit.projectDir, "build/docs/asciidoc/css/stylesheet.css")).exists()
        }
    }

    @Test
    fun asciidocWhenMissingAttributeThenFailure() {
        TestKit().use { testKit ->
            val build = testKit
                    .withProjectResource(projectResource("missing-attribute"))
                    .withArguments(ASCIIDOCTOR_TASK_NAME)
                    .buildAndFail()
            assertThat(build.task(ASCIIDOCTOR_TASK_NAME)?.outcome).isEqualTo(TaskOutcome.FAILED)
        }
    }

    private fun projectResource(name: String) = "convention/asciidoctor/${name}"
}