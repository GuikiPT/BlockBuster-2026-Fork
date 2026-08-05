from __future__ import annotations

import json
from pathlib import Path

ROOT = Path.cwd()
RESOURCES = ROOT / "src" / "main" / "resources"

BUILD_GRADLE = r'''plugins {
    id 'dev.architectury.loom' version '1.9-SNAPSHOT'
}

version = project.mod_version + "-neoforge-" + project.minecraft_version
group = project.maven_group

base {
    archivesName = project.archives_base_name
}

sourceSets {
    main {
        java.srcDirs = ['src/main/java', 'src/client/java']
        resources.srcDirs = ['src/main/resources', 'src/client/resources']
    }
}

loom {
    accessWidenerPath = file("src/main/resources/blockbuster.accesswidener")

    mods {
        blockbuster {
            sourceSet sourceSets.main
        }
    }

    runs {
        client {
            client()
            name = "NeoForge Client"
        }
        server {
            server()
            name = "NeoForge Server"
        }
    }
}

repositories {
    mavenCentral()
    maven {
        name = "Fabric"
        url = "https://maven.fabricmc.net/"
    }
    maven {
        name = "NeoForged"
        url = "https://maven.neoforged.net/releases"
    }
    maven {
        name = "Sinytra"
        url = "https://maven.su5ed.dev/releases"
    }
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = "https://api.modrinth.com/maven"
            }
        }
        filter {
            includeGroup "maven.modrinth"
        }
    }
}

dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"
    neoForge "net.neoforged:neoforge:${project.neoforge_version}"

    /*
     * BlockBuster's 1.21.1 source already targets the public Fabric API.
     * Forgified Fabric API implements that API on NeoForge, allowing the
     * stabilized gameplay code to remain shared while loader-specific pieces
     * are migrated incrementally.
     */
    modImplementation "org.sinytra.forgified-fabric-api:forgified-fabric-api:${project.ffapi_version}"
    compileOnly "org.sinytra:forgified-fabric-loader:${project.ffl_version}"
    runtimeOnly "org.sinytra:forgified-fabric-loader:${project.ffl_version}:full"

    implementation "javax.vecmath:vecmath:1.5.2"
    include "javax.vecmath:vecmath:1.5.2"

    /* Exact NeoForge counterpart of the Fabric compile-only Iris pin. */
    modCompileOnly "maven.modrinth:iris:${project.iris_version}"
}

processResources {
    inputs.property "version", project.version

    filesMatching("META-INF/neoforge.mods.toml") {
        expand "version": project.version
    }
}

tasks.matching {
    it.name in ["test", "compileTestJava", "processTestResources", "testClasses"]
}.configureEach {
    enabled = false
}

tasks.withType(JavaCompile).configureEach {
    options.release = 21
    options.compilerArgs += ['-Xmaxerrs', '1000']
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

remapJar {
    /* Convert the Yarn-named access widener into a NeoForge access transformer. */
    atAccessWideners.add("blockbuster.accesswidener")
}

jar {
    preserveFileTimestamps = false
    reproducibleFileOrder = true

    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
    from("ATTRIBUTION.md") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}
'''

SETTINGS_GRADLE = r'''pluginManagement {
    repositories {
        maven {
            name = 'Architectury'
            url = 'https://maven.architectury.dev/'
        }
        maven {
            name = 'Fabric'
            url = 'https://maven.fabricmc.net/'
        }
        maven {
            name = 'NeoForged'
            url = 'https://maven.neoforged.net/releases'
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "blockbuster"
'''

GRADLE_PROPERTIES = r'''org.gradle.jvmargs=-Xmx4G
org.gradle.parallel=true
org.gradle.caching=true

loom.platform=neoforge

minecraft_version=1.21.1
yarn_mappings=1.21.1+build.3
neoforge_version=21.1.219

mod_version=2.7.2
maven_group=mchorse
archives_base_name=blockbuster

# Fabric API implemented natively on NeoForge.
ffapi_version=0.116.7+2.2.4+1.21.1
ffl_version=2.5.68+0.18.4+1.21.1

# Exact NeoForge Iris build corresponding to the Fabric 1.8.8 pin.
iris_version=1.8.8+1.21.1-neoforge
'''

NEOFORGE_MODS_TOML = r'''modLoader="javafml"
loaderVersion="[4,)"
license="GPL-3.0-only"
issueTrackerURL="https://github.com/GuikiPT/BlockBuster-2026-Fork/issues"

[[mods]]
modId="blockbuster"
version="${version}"
displayName="Blockbuster"
displayURL="https://github.com/GuikiPT/BlockBuster-2026-Fork"
authors="McHorse; BlockBuster 1.21.1 port contributors"
description="Design, direct and act in your own Minecraft machinimas. This NeoForge build ports BlockBuster 2.7.2 together with McLib, Metamorph, Aperture and Chameleon."

[[mixins]]
config="blockbuster.mixins.json"

[[mixins]]
config="blockbuster.client.mixins.json"

[[mixins]]
config="metamorph.mixins.json"

[[mixins]]
config="metamorph.client.mixins.json"

[[mixins]]
config="blockbuster.iris.mixins.json"

[[dependencies.blockbuster]]
modId="neoforge"
type="required"
versionRange="[21.1.169,)"
ordering="NONE"
side="BOTH"

[[dependencies.blockbuster]]
modId="minecraft"
type="required"
versionRange="[1.21.1,1.22)"
ordering="NONE"
side="BOTH"

[[dependencies.blockbuster]]
modId="forgified_fabric_api"
type="required"
versionRange="[0.116.7,)"
ordering="AFTER"
side="BOTH"
'''

NEOFORGE_ENTRYPOINT = r'''package mchorse.blockbuster.neoforge;

import mchorse.blockbuster.Blockbuster;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Native NeoForge entrypoint for the shared 1.21.1 BlockBuster source. */
@Mod(Blockbuster.MOD_ID)
public final class BlockbusterNeoForge
{
    public BlockbusterNeoForge(IEventBus modEventBus, Dist dist)
    {
        new Blockbuster().onInitialize();

        if (dist == Dist.CLIENT)
        {
            NeoForgeClientBootstrap.initialize();
        }
    }
}
'''

NEOFORGE_CLIENT_BOOTSTRAP = r'''package mchorse.blockbuster.neoforge;

import mchorse.blockbuster.BlockbusterClient;

/**
 * Kept in a separate class so the dedicated server never resolves client-only
 * Minecraft classes while loading the common NeoForge entrypoint.
 */
final class NeoForgeClientBootstrap
{
    private NeoForgeClientBootstrap()
    {}

    static void initialize()
    {
        new BlockbusterClient().onInitializeClient();
    }
}
'''

WRAPPER_PROPERTIES = r'''distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.14-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
'''


def write(relative: str, text: str) -> None:
    path = ROOT / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.rstrip() + "\n", encoding="utf-8")


def strip_refmaps() -> None:
    for name in (
        "blockbuster.client.mixins.json",
        "blockbuster.iris.mixins.json",
        "metamorph.client.mixins.json",
    ):
        path = RESOURCES / name
        if not path.exists():
            continue
        data = json.loads(path.read_text(encoding="utf-8"))
        data.pop("refmap", None)
        path.write_text(json.dumps(data, indent="\t") + "\n", encoding="utf-8")


write("build.gradle", BUILD_GRADLE)
write("settings.gradle", SETTINGS_GRADLE)
write("gradle.properties", GRADLE_PROPERTIES)
write("gradle/wrapper/gradle-wrapper.properties", WRAPPER_PROPERTIES)
write("src/main/resources/META-INF/neoforge.mods.toml", NEOFORGE_MODS_TOML)
write("src/main/java/mchorse/blockbuster/neoforge/BlockbusterNeoForge.java", NEOFORGE_ENTRYPOINT)
write("src/client/java/mchorse/blockbuster/neoforge/NeoForgeClientBootstrap.java", NEOFORGE_CLIENT_BOOTSTRAP)

fabric_metadata = RESOURCES / "fabric.mod.json"
if fabric_metadata.exists():
    fabric_metadata.unlink()

strip_refmaps()

print("Converted generated BlockBuster 1.21.1 source to the NeoForge/Architectury Loom workspace.")
