workspace(name = "jgit")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("//tools:bazlets.bzl", "load_bazlets")

load_bazlets(commit = "f30a992da9fc855dce819875afb59f9dd6f860cd")

load(
    "@com_googlesource_gerrit_bazlets//tools:maven_jar.bzl",
    "maven_jar",
)

http_archive(
    name = "rbe_jdk11",
    sha256 = "dbcfd6f26589ef506b91fe03a12dc559ca9c84699e4cf6381150522287f0e6f6",
    strip_prefix = "rbe_autoconfig-3.1.0",
    urls = [
        "https://gerrit-bazel.storage.googleapis.com/rbe_autoconfig/v3.1.0.tar.gz",
        "https://github.com/davido/rbe_autoconfig/archive/v3.1.0.tar.gz",
    ],
)

register_toolchains("//tools:error_prone_warnings_toolchain_java11_definition")

register_toolchains("//tools:error_prone_warnings_toolchain_java17_definition")

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
    name = "eddsa",
    artifact = "net.i2p.crypto:eddsa:0.3.0",
    sha1 = "1901c8d4d8bffb7d79027686cfb91e704217c3e1",
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

SSHD_VERS = "2.11.0"

maven_jar(
    name = "sshd-osgi",
    artifact = "org.apache.sshd:sshd-osgi:" + SSHD_VERS,
    sha1 = "7ec6b14ab789fc4b1ce9fdcd0e13d22b5c940e7b",
)

maven_jar(
    name = "sshd-sftp",
    artifact = "org.apache.sshd:sshd-sftp:" + SSHD_VERS,
    sha1 = "3a293bba303c486a9ff6be8e11c9c68fd56b63c7",
)

maven_jar(
    name = "jna",
    artifact = "net.java.dev.jna:jna:5.13.0",
    sha1 = "1200e7ebeedbe0d10062093f32925a912020e747",
)

maven_jar(
    name = "jna-platform",
    artifact = "net.java.dev.jna:jna-platform:5.13.0",
    sha1 = "88e9a306715e9379f3122415ef4ae759a352640d",
)

maven_jar(
    name = "commons-codec",
    artifact = "commons-codec:commons-codec:1.16.0",
    sha1 = "4e3eb3d79888d76b54e28b350915b5dc3919c9de",
)

maven_jar(
    name = "commons-logging",
    artifact = "commons-logging:commons-logging:1.2",
    sha1 = "4bfc12adfe4842bf07b657f0369c4cb522955686",
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
    artifact = "jakarta.servlet:jakarta.servlet-api:4.0.4",
    sha1 = "b8a1142e04838fe54194049c6e7a18dae8f9b960",
)

maven_jar(
    name = "commons-compress",
    artifact = "org.apache.commons:commons-compress:1.24.0",
    sha1 = "b4b1b5a3d9573b2970fddab236102c0a4d27d35e",
)

maven_jar(
    name = "tukaani-xz",
    artifact = "org.tukaani:xz:1.9",
    sha1 = "1ea4bec1a921180164852c65006d928617bd2caf",
)

maven_jar(
    name = "args4j",
    artifact = "args4j:args4j:2.33",
    sha1 = "bd87a75374a6d6523de82fef51fc3cfe9baf9fc9",
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
    artifact = "org.mockito:mockito-core:5.5.0",
    sha1 = "1660ec3ce0af7f713af923817b225a37cc5cf965",
)

maven_jar(
    name = "assertj-core",
    artifact = "org.assertj:assertj-core:3.24.2",
    sha1 = "ebbf338e33f893139459ce5df023115971c2786f",
)

BYTE_BUDDY_VERSION = "1.14.8"

maven_jar(
    name = "bytebuddy",
    artifact = "net.bytebuddy:byte-buddy:" + BYTE_BUDDY_VERSION,
    sha1 = "505d7d8937ff00cc55db79723e26c94069b87d66",
)

maven_jar(
    name = "bytebuddy-agent",
    artifact = "net.bytebuddy:byte-buddy-agent:" + BYTE_BUDDY_VERSION,
    sha1 = "ae6ebe485f3bcd0a1e20241488a32e6400a501ef",
)

maven_jar(
    name = "objenesis",
    artifact = "org.objenesis:objenesis:3.3",
    sha1 = "1049c09f1de4331e8193e579448d0916d75b7631",
)

maven_jar(
    name = "gson",
    artifact = "com.google.code.gson:gson:2.10.1",
    sha1 = "b3add478d4382b78ea20b1671390a858002feb6c",
)

JETTY_VER = "10.0.16"

maven_jar(
    name = "jetty-servlet",
    artifact = "org.eclipse.jetty:jetty-servlet:" + JETTY_VER,
    sha1 = "3b995aca89a81109672587f81d411efbd0530d2a",
    src_sha1 = "52fe27e7afe4e8759b4f283fe7d2c047f4c1119d",
)

maven_jar(
    name = "jetty-security",
    artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VER,
    sha1 = "7735aba773abefb79f0f68e058bf7b303df9364e",
    src_sha1 = "9f5edba151781d9997b3f4e4d4b7394777176555",
)

maven_jar(
    name = "jetty-server",
    artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VER,
    sha1 = "fefaa98e95b9737562d196d24f7846734ce99e17",
    src_sha1 = "466af604e91b390ef2d9dccf7b115a58a280ac88",
)

maven_jar(
    name = "jetty-http",
    artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VER,
    sha1 = "0bf1c1db5bb6e97c1040ee5c1053ec46b9895c5a",
    src_sha1 = "30381054b9f3e532dad731d7657ddcae40614342",
)

maven_jar(
    name = "jetty-io",
    artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VER,
    sha1 = "a96c25a805a64a39a7c6e81dbd779bd228ca13aa",
    src_sha1 = "8178ce7430fe98e02b3221610cf5ad057c8489d3",
)

maven_jar(
    name = "jetty-util",
    artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VER,
    sha1 = "1513b0c4d8ce627835a5511950d4c40be6450258",
    src_sha1 = "297da3bb10874c8ed63615c7116b5a8a74c4adcb",
)

maven_jar(
    name = "jetty-util-ajax",
    artifact = "org.eclipse.jetty:jetty-util-ajax:" + JETTY_VER,
    sha1 = "234c1c2a259ab10ed4cc9e24c447b08ac9e1fb69",
    src_sha1 = "1b0548113863a8bfd9b94f8ab36a5a288cc0096e",
)

BOUNCYCASTLE_VER = "1.76"

maven_jar(
    name = "bcpg",
    artifact = "org.bouncycastle:bcpg-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "d5c23d0470261254d0e84dde1d4237d228540298",
    src_sha1 = "68d49cdd07da2121a904f481e2e92ca864c08d05",
)

maven_jar(
    name = "bcprov",
    artifact = "org.bouncycastle:bcprov-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "3a785d0b41806865ad7e311162bfa3fa60b3965b",
    src_sha1 = "9e00748625819d7e3cc1447366dfa76f0b354a2d",
)

maven_jar(
    name = "bcutil",
    artifact = "org.bouncycastle:bcutil-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "8c7594e651a278bcde18e038d8ab55b1f97f4d31",
    src_sha1 = "836bb2c42f10b29127b470ebe5c648927dd4ddc6",
)

maven_jar(
    name = "bcpkix",
    artifact = "org.bouncycastle:bcpkix-jdk18on:" + BOUNCYCASTLE_VER,
    sha1 = "10c9cf5c1b4d64abeda28ee32fbade3b74373622",
    src_sha1 = "e5700c1de407652c1af5961ac8a04fab02eda365",
)
