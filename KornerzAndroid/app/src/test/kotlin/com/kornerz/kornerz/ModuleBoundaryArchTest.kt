package com.kornerz.kornerz

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The module-boundary rule from `docs/architecture.md` §2.5, enforced
 * at test time (`:177`, `:262`, `:387` mandate ArchUnit for P10).
 *
 * The rules encode the rule as it is **actually practised**:
 *
 *  - `core.*` never depends on `feature.*` or on `app`;
 *  - `feature.*` never depends on `app`;
 *  - no feature module depends on another feature module **except**
 *    `feature.shell`, which owns the tab-graph seam.
 *
 * **Why the test lives in `app`.** `app` is the only module whose
 * unit-test runtime classpath holds every module's classes —
 * `testImplementation` extends `implementation` — so a single
 * `importPackages("com.kornerz")` sees the whole tree. A test in any
 * other module would only see that module's own classes and would
 * pass vacuously.
 */
class ModuleBoundaryArchTest {

    private val classes: JavaClasses = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.kornerz")

    @Test
    fun `core modules never depend on feature modules or on the app`() {
        noClasses()
            .that()
            .resideInAPackage(CORE_PACKAGE)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(FEATURE_PACKAGE, APP_PACKAGE)
            .check(classes)
    }

    @Test
    fun `feature modules never depend on the app`() {
        noClasses()
            .that()
            .resideInAPackage(FEATURE_PACKAGE)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(APP_PACKAGE)
            .check(classes)
    }

    @Test
    fun `no feature module depends on another feature module except the shell`() {
        // `feature.shell` owns the tab graph, so it is allowed to reach
        // every other feature module. Every other feature module is
        // checked against every *other* feature module — a module may
        // depend on itself, never on its neighbour.
        for (module in FEATURE_MODULES) {
            if (module == SHELL_MODULE) continue
            val forbidden = FEATURE_MODULES
                .filter { it != module && it != SHELL_MODULE }
                .map { "$it.." }

            noClasses()
                .that()
                .resideInAPackage("$module..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(*forbidden.toTypedArray())
                .check(classes)
        }
    }

    @Test
    fun `the shell's tab-graph seam is allowed`() {
        // The positive counterpart of the rule above: the seam the
        // architecture documents must not be flagged by accident — and
        // it must actually exist, otherwise the exclusion above would
        // be carving out an empty package.
        val shellClasses = classes.filter { candidate ->
            candidate.packageName.startsWith("$SHELL_MODULE.")
        }
        val reachesFeatureModules = shellClasses.any { shellClass ->
            shellClass.directDependenciesFromSelf.any { dependency ->
                dependency.targetClass.packageName.startsWith("com.kornerz.feature.")
            }
        }
        assertTrue(
            "feature.shell must reach the feature modules through the tab-graph seam",
            reachesFeatureModules,
        )
    }

    private companion object {
        const val CORE_PACKAGE = "com.kornerz.core.."
        const val FEATURE_PACKAGE = "com.kornerz.feature.."

        /** The `app` module's namespace — `app/build.gradle.kts`. */
        const val APP_PACKAGE = "com.kornerz.kornerz.."

        const val SHELL_MODULE = "com.kornerz.feature.shell"

        /** Every feature module, by its root package. */
        val FEATURE_MODULES = listOf(
            "com.kornerz.feature.auth",
            "com.kornerz.feature.community",
            "com.kornerz.feature.home",
            "com.kornerz.feature.kornerz",
            "com.kornerz.feature.loops",
            "com.kornerz.feature.nooks",
            "com.kornerz.feature.notifications",
            "com.kornerz.feature.profile",
            "com.kornerz.feature.ranking",
            "com.kornerz.feature.shell",
            "com.kornerz.feature.splash",
            "com.kornerz.feature.subscription",
            "com.kornerz.feature.videocall",
        )
    }
}
