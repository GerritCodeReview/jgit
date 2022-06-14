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
    sha256 = "766796de71916118e528b9f4334c29c9c9b4e926227bf3264dee555e6a4306c8",
    strip_prefix = "rbe_autoconfig-2.0.0",
    urls = [
        "https://gerrit-bazel.storage.googleapis.com/rbe_autoconfig/v2.0.0.tar.gz",
        "https://github.com/davido/rbe_autoconfig/archive/v2.0.0.tar.gz",
    ],
)

register_toolchains("//tools:error_prone_warnings_toolchain_java11_definition")

register_toolchains("//tools:error_prone_warnings_toolchain_java17_definition")

JMH_VERS = "1.32"

maven_jar(
    name = "jmh-core",
    artifact = "org.openjdk.jmh:jmh-core:" + JMH_VERS,
    attach_source = False,
    sha1 = "9a8b69ea08118fd4e5d30a152d37b7087ee4a720",
)

maven_jar(
    name = "jmh-annotations",
    artifact = "org.openjdk.jmh:jmh-generator-annprocess:" + JMH_VERS,
    attach_source = False,
    sha1 = "0a28eccc75e0d65984ce25e1ec4dd021a0ca6c57",
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
    artifact = "com.googlecode.javaewah:JavaEWAH:1.1.13",
    sha1 = "32cd724a42dc73f99ca08453d11a4bb83e0034c7",
)

maven_jar(
    name = "httpclient",
    artifact = "org.apache.httpcomponents:httpclient:4.5.13",
    sha1 = "e5f6cae5ca7ecaac1ec2827a9e2d65ae2869cada",
)

maven_jar(
    name = "httpcore",
    artifact = "org.apache.httpcomponents:httpcore:4.4.14",
    sha1 = "9dd1a631c082d92ecd4bd8fd4cf55026c720a8c1",
)

maven_jar(
    name = "sshd-osgi",
    artifact = "org.apache.sshd:sshd-osgi:2.8.0",
    sha1 = "b2a59b73c045f40d5722b9160d4f909a646d86c9",
)

maven_jar(
    name = "sshd-sftp",
    artifact = "org.apache.sshd:sshd-sftp:2.8.0",
    sha1 = "d3cd9bc8d335b3ed1a86d2965deb4d202de27442",
)

maven_jar(
    name = "jna",
    artifact = "net.java.dev.jna:jna:5.8.0",
    sha1 = "3551d8d827e54858214107541d3aff9c615cb615",
)

maven_jar(
    name = "jna-platform",
    artifact = "net.java.dev.jna:jna-platform:5.8.0",
    sha1 = "2f12f6d7f7652270d13624cef1b82d8cd9a5398e",
)

maven_jar(
    name = "commons-codec",
    artifact = "commons-codec:commons-codec:1.14",
    sha1 = "3cb1181b2141a7e752f5bdc998b7ef1849f726cf",
)

maven_jar(
    name = "commons-logging",
    artifact = "commons-logging:commons-logging:1.2",
    sha1 = "4bfc12adfe4842bf07b657f0369c4cb522955686",
)

maven_jar(
    name = "log-api",
    artifact = "org.slf4j:slf4j-api:1.7.30",
    sha1 = "b5a4b6d16ab13e34a88fae84c35cd5d68cac922c",
)

maven_jar(
    name = "slf4j-simple",
    artifact = "org.slf4j:slf4j-simple:1.7.30",
    sha1 = "e606eac955f55ecf1d8edcccba04eb8ac98088dd",
)

maven_jar(
    name = "servlet-api",
    artifact = "jakarta.servlet:jakarta.servlet-api:5.0.0",
    sha1 = "2e6b8ccde55522c879434ddec3714683ccae6867",
)

maven_jar(
    name = "commons-compress",
    artifact = "org.apache.commons:commons-compress:1.21",
    sha1 = "4ec95b60d4e86b5c95a0e919cb172a0af98011ef",
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
    artifact = "org.mockito:mockito-core:2.23.0",
    sha1 = "497ddb32fd5d01f9dbe99a2ec790aeb931dff1b1",
)

maven_jar(
    name = "assertj-core",
    artifact = "org.assertj:assertj-core:3.20.2",
    sha1 = "66f1f0ebd6db2b24e4a731979171da16ba919cd5",
)

BYTE_BUDDY_VERSION = "1.9.0"

maven_jar(
    name = "bytebuddy",
    artifact = "net.bytebuddy:byte-buddy:" + BYTE_BUDDY_VERSION,
    sha1 = "8cb0d5baae526c9df46ae17693bbba302640538b",
)

maven_jar(
    name = "bytebuddy-agent",
    artifact = "net.bytebuddy:byte-buddy-agent:" + BYTE_BUDDY_VERSION,
    sha1 = "37b5703b4a6290be3fffc63ae9c6bcaaee0ff856",
)

maven_jar(
    name = "objenesis",
    artifact = "org.objenesis:objenesis:2.6",
    sha1 = "639033469776fd37c08358c6b92a4761feb2af4b",
)

maven_jar(
    name = "gson",
    artifact = "com.google.code.gson:gson:2.8.9",
    sha1 = "8a432c1d6825781e21a02db2e2c33c5fde2833b9",
)

JETTY_VER = "11.0.9"

maven_jar(
    name = "jetty-servlet",
    artifact = "org.eclipse.jetty:jetty-servlet:" + JETTY_VER,
    sha1 = "c889c26e88fcba7b210e710709396b263b836caf",
    src_sha1 = "04441bc33cd66c0e329473480ffce23f31237350",
)

maven_jar(
    name = "jetty-security",
    artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VER,
    sha1 = "f9de6ce9efd65abffef363e243414d9807716f5e",
    src_sha1 = "6982f19c55d63891ae4a507874d8eb0b1b549b9c",
)

maven_jar(
    name = "jetty-server",
    artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VER,
    sha1 = "22b9a517b7fc7e28cc86f31322598771e4f6327e",
    src_sha1 = "f9ea3e684641deab9c562ae71bb57c9ff12560c4",
)

maven_jar(
    name = "jetty-http",
    artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VER,
    sha1 = "c1c50528139df22f6f75a6596d5e0aee68267b94",
    src_sha1 = "4bbcdd3e3149d3002ced694ce8016403498b8a91",
)

maven_jar(
    name = "jetty-io",
    artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VER,
    sha1 = "f906655ea863c27f2c28277e61ca90b238c6b370",
    src_sha1 = "54e6784a79fcfd20b07c40fe65c0060bea249869",
)

maven_jar(
    name = "jetty-util",
    artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VER,
    sha1 = "85160561521d99eb61f2be23c2a2c8c57bbf4c25",
    src_sha1 = "652b29358ffd3e1d6120931ae1347293db937cf9",
)

maven_jar(
    name = "jetty-util-ajax",
    artifact = "org.eclipse.jetty:jetty-util-ajax:" + JETTY_VER,
    sha1 = "500a7b674fad7405d859dda541988ca142462ac3",
    src_sha1 = "8e759f0a477fe4418c06f6b176ee4e83a2f8c1ae",
)

BOUNCYCASTLE_VER = "1.70"

maven_jar(
    name = "bcpg",
    artifact = "org.bouncycastle:bcpg-jdk15on:" + BOUNCYCASTLE_VER,
    sha1 = "062f72ec06f31a6c31a3f3355fce0384b21126d7",
    src_sha1 = "9dee73ad926752ee3b421a7dc4531287166ccf36",
)

maven_jar(
    name = "bcprov",
    artifact = "org.bouncycastle:bcprov-jdk15on:" + BOUNCYCASTLE_VER,
    sha1 = "4636a0d01f74acaf28082fb62b317f1080118371",
    src_sha1 = "6245e15dd47e5fc33cff275df61662e0a8e5958f",
)

maven_jar(
    name = "bcutil",
    artifact = "org.bouncycastle:bcutil-jdk15on:" + BOUNCYCASTLE_VER,
    sha1 = "54280e7195a7430d7911ded93fc01e07300b9526",
    src_sha1 = "4af4a6c92b8ea07885b27d8536b81b855497f4eb",
)

maven_jar(
    name = "bcpkix",
    artifact = "org.bouncycastle:bcpkix-jdk15on:" + BOUNCYCASTLE_VER,
    sha1 = "f81e5af49571a9d5a109a88f239a73ce87055417",
    src_sha1 = "42f9de53a91b20bc06e88482c57fd97e5a84250d",
)
