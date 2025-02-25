workspace(name = "jgit")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("//tools:bazlets.bzl", "load_bazlets")

load_bazlets(commit = "f9c119e45d9a241bee720b7fbd6c7fdbc952da5f")

load(
    "@com_googlesource_gerrit_bazlets//tools:maven_jar.bzl",
    "maven_jar",
)

http_archive(
    name = "rules_java",
    sha256 = "4da3761f6855ad916568e2bfe86213ba6d2637f56b8360538a7fb6125abf6518",
    urls = [
        "https://github.com/bazelbuild/rules_java/releases/download/7.5.0/rules_java-7.5.0.tar.gz",
    ],
)

load("@rules_java//java:repositories.bzl", "rules_java_dependencies", "rules_java_toolchains")

rules_java_dependencies()

http_archive(
    name = "ubuntu2204_jdk17",
    sha256 = "8ea82b81c9707e535ff93ef5349d11e55b2a23c62bcc3b0faaec052144aed87d",
    strip_prefix = "rbe_autoconfig-5.1.0",
    urls = [
        "https://gerrit-bazel.storage.googleapis.com/rbe_autoconfig/v5.1.0.tar.gz",
        "https://github.com/davido/rbe_autoconfig/releases/download/v5.1.0/v5.1.0.tar.gz",
    ],
)

register_toolchains("//tools:error_prone_warnings_toolchain_java17_definition")

register_toolchains("//tools:error_prone_warnings_toolchain_java21_definition")

# Order of registering toolchains matters. rules_java toolchains take precedence
# over the custom toolchains, so the default jdk21 toolchain gets picked
# (one without custom package_config). That's why the `rules_java_toolchains()`
# must be called after the `register_toolchain()` invocation.
rules_java_toolchains()

JMH_VERS = "1.37"

maven_jar(
    name = "jmh-core",
    artifact = "org.openjdk.jmh:jmh-core:" + JMH_VERS,
    attach_source = False,
    sha1 = "896f27e49105b35ea1964319c83d12082e7a79ef",
)

maven_jar(
    name = "jmh-annotations",
    artifact = "org.openjdk.jmh:jmh-generator-annprocess:" + JMH_VERS,
    attach_source = False,
    sha1 = "da93888682df163144edf9b13d2b78e54166063a",
)

maven_jar(
    name = "jopt",
    artifact = "net.sf.jopt-simple:jopt-simple:5.0.4",
    attach_source = False,
    sha1 = "4fdac2fbe92dfad86aa6e9301736f6b4342a3f5c",
)

maven_jar(
    name = "math3",
    artifact = "org.apache.commons:commons-math3:3.6.1",
    attach_source = False,
    sha1 = "e4ba98f1d4b3c80ec46392f25e094a6a2e58fcbf",
)

maven_jar(
    name = "jsch",
    artifact = "com.jcraft:jsch:0.1.55",
    sha1 = "bbd40e5aa7aa3cfad5db34965456cee738a42a50",
)

maven_jar(
    name = "jzlib",
    artifact = "com.jcraft:jzlib:1.1.3",
    sha1 = "c01428efa717624f7aabf4df319939dda9646b2d",
)

maven_jar(
    name = "javaewah",
    artifact = "com.googlecode.javaewah:JavaEWAH:1.2.3",
    sha1 = "13a27c856e0c8808cee9a64032c58eee11c3adc9",
)

maven_jar(
    name = "httpclient",
    artifact = "org.apache.httpcomponents:httpclient:4.5.14",
    sha1 = "1194890e6f56ec29177673f2f12d0b8e627dec98",
)

maven_jar(
    name = "httpcore",
    artifact = "org.apache.httpcomponents:httpcore:4.4.16",
    sha1 = "51cf043c87253c9f58b539c9f7e44c8894223850",
)

SSHD_VERS = "2.15.0"

maven_jar(
    name = "sshd-osgi",
    artifact = "org.apache.sshd:sshd-osgi:" + SSHD_VERS,
    sha1 = "aa76898fe47eab7da0878dd60e6f3be5631e076c",
)

maven_jar(
    name = "sshd-sftp",
    artifact = "org.apache.sshd:sshd-sftp:" + SSHD_VERS,
    sha1 = "2e226055ed060c64ed76256a9c45de6d0109eef8",
)

JNA_VERS = "5.16.0"

maven_jar(
    name = "jna",
    artifact = "net.java.dev.jna:jna:" + JNA_VERS,
    sha1 = "ebea09f91dc9f7048099f963fb8d6f919f0a4d9c",
)

maven_jar(
    name = "jna-platform",
    artifact = "net.java.dev.jna:jna-platform:" + JNA_VERS,
    sha1 = "b2a9065f97c166893d504b164706512338e3bbc2",
)

maven_jar(
    name = "commons-codec",
    artifact = "commons-codec:commons-codec:1.18.0",
    sha1 = "ee45d1cf6ec2cc2b809ff04b4dc7aec858e0df8f",
)

maven_jar(
    name = "commons-logging",
    artifact = "commons-logging:commons-logging:1.3.5",
    sha1 = "a3fcc5d3c29b2b03433aa2d2f2d2c1b1638924a1",
)

maven_jar(
    name = "log-api",
    artifact = "org.slf4j:slf4j-api:1.7.36",
    sha1 = "6c62681a2f655b49963a5983b8b0950a6120ae14",
)

maven_jar(
    name = "slf4j-simple",
    artifact = "org.slf4j:slf4j-simple:1.7.36",
    sha1 = "a41f9cfe6faafb2eb83a1c7dd2d0dfd844e2a936",
)

maven_jar(
    name = "servlet-api",
    artifact = "jakarta.servlet:jakarta.servlet-api:6.1.0",
    sha1 = "1169a246913fe3823782af7943e7a103634867c5",
)

maven_jar(
    name = "commons-compress",
    artifact = "org.apache.commons:commons-compress:1.27.1",
    sha1 = "a19151084758e2fbb6b41eddaa88e7b8ff4e6599",
)

maven_jar(
    name = "commons-lang3",
    artifact = "org.apache.commons:commons-lang3:3.17.0",
    sha1 = "b17d2136f0460dcc0d2016ceefca8723bdf4ee70",
)

maven_jar(
    name = "commons-io",
    artifact = "commons-io:commons-io:2.18.0",
    sha1 = "44084ef756763795b31c578403dd028ff4a22950",
)

maven_jar(
    name = "tukaani-xz",
    artifact = "org.tukaani:xz:1.10",
    sha1 = "1be8166f89e035a56c6bfc67dbc423996fe577e2",
)

maven_jar(
    name = "args4j",
    artifact = "args4j:args4j:2.37",
    sha1 = "244f60c057d72a785227c0562d3560f42a7ea54b",
)

maven_jar(
    name = "junit",
    artifact = "junit:junit:4.13.2",
    sha1 = "8ac9e16d933b6fb43bc7f576336b8f4d7eb5ba12",
)

maven_jar(
    name = "hamcrest",
    artifact = "org.hamcrest:hamcrest:2.2",
    sha1 = "1820c0968dba3a11a1b30669bb1f01978a91dedc",
)

maven_jar(
    name = "mockito",
    artifact = "org.mockito:mockito-core:5.15.2",
    sha1 = "87be4b1e0cc5febc07ab3197a8ff3ede56b37a79",
)

maven_jar(
    name = "assertj-core",
    artifact = "org.assertj:assertj-core:3.27.3",
    sha1 = "31f5d58a202bd5df4993fb10fa2cffd610c20d6f",
)

BYTE_BUDDY_VERSION = "1.17.1"

maven_jar(
    name = "bytebuddy",
    artifact = "net.bytebuddy:byte-buddy:" + BYTE_BUDDY_VERSION,
    sha1 = "8b5205fad48196a88d3d66dddff5a7417bce3596",
)

maven_jar(
    name = "bytebuddy-agent",
    artifact = "net.bytebuddy:byte-buddy-agent:" + BYTE_BUDDY_VERSION,
    sha1 = "0669a13b59d5ffd8198a79e4dc99018a9278e457",
)

maven_jar(
    name = "objenesis",
    artifact = "org.objenesis:objenesis:3.4",
    sha1 = "675cbe121a68019235d27f6c34b4f0ac30e07418",
)

maven_jar(
    name = "gson",
    artifact = "com.google.code.gson:gson:2.12.1",
    sha1 = "4e773a317740b83b43cfc3d652962856041697cb",
)

JETTY_VER = "12.0.16"

maven_jar(
    name = "jetty-servlet",
    artifact = "org.eclipse.jetty.ee10:jetty-ee10-servlet:" + JETTY_VER,
    sha1 = "022a746c00b1ac5c790fee65a398c707160a46d8",
)

maven_jar(
    name = "jetty-security",
    artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VER,
    sha1 = "23b1a3abecf9d6f5498064a32d9145ae1d8330f9",
)

maven_jar(
    name = "jetty-server",
    artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VER,
    sha1 = "3e3638b4bfbee04c27b3ae68e4949fc43b40a042",
)

maven_jar(
    name = "jetty-session",
    artifact = "org.eclipse.jetty:jetty-session:" + JETTY_VER,
    sha1 = "79cdedc7afebbdba4453f603dfe2f970baa35cc3",
)

maven_jar(
    name = "jetty-http",
    artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VER,
    sha1 = "68019fa90e8420ae15c109bd8c8611cacbaf43e5",
)

maven_jar(
    name = "jetty-io",
    artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VER,
    sha1 = "7a162c537a99bbaf35a074fec9a50815e6c81d9d",
)

maven_jar(
    name = "jetty-util",
    artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VER,
    sha1 = "e262e505363e5925df15618622d9888aefc1b0d0",
)

maven_jar(
    name = "jetty-util-ajax",
    artifact = "org.eclipse.jetty:jetty-util-ajax:" + JETTY_VER,
    sha1 = "60225034131e3f771b40bc75c15bd9cc4952302b",
)

BOUNCYCASTLE_VER = "1.80"

maven_jar(
    name = "bcpg",
    artifact = "org.bouncycastle:bcpg-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "163889a825393854dbe7dc52f1a8667e715e9859",
)

maven_jar(
    name = "bcprov",
    artifact = "org.bouncycastle:bcprov-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "e22100b41042decf09cab914a5af8d2c57b5ac4a",
)

maven_jar(
    name = "bcutil",
    artifact = "org.bouncycastle:bcutil-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "b95726d1d49a0c65010c59a3e6640311d951bfd1",
)

maven_jar(
    name = "bcpkix",
    artifact = "org.bouncycastle:bcpkix-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "5277dfaaef2e92ce1d802499599a0ca7488f86e6",
)
