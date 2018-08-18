workspace(name = "jgit")

load("//tools:bazlets.bzl", "load_bazlets")

load_bazlets(commit = "3afbeab55ece585dbfc7a980bf7214b24ddbbe86")

load(
    "@com_googlesource_gerrit_bazlets//tools:maven_jar.bzl",
    "maven_jar",
)

maven_jar(
    name = "jsch",
    artifact = "com.jcraft:jsch:0.1.54",
    sha1 = "da3584329a263616e277e15462b387addd1b208d",
)

maven_jar(
    name = "jzlib",
    artifact = "com.jcraft:jzlib:1.1.1",
    sha1 = "a1551373315ffc2f96130a0e5704f74e151777ba",
)

maven_jar(
    name = "javaewah",
    artifact = "com.googlecode.javaewah:JavaEWAH:1.1.6",
    sha1 = "94ad16d728b374d65bd897625f3fbb3da223a2b6",
)

maven_jar(
    name = "httpclient",
    artifact = "org.apache.httpcomponents:httpclient:4.5.5",
    sha1 = "1603dfd56ebcd583ccdf337b6c3984ac55d89e58",
)

maven_jar(
    name = "httpcore",
    artifact = "org.apache.httpcomponents:httpcore:4.4.9",
    sha1 = "a86ce739e5a7175b4b234c290a00a5fdb80957a0",
)

maven_jar(
    name = "commons_codec",
    artifact = "commons-codec:commons-codec:1.10",
    sha1 = "4b95f4897fa13f2cd904aee711aeafc0c5295cd8",
)

maven_jar(
    name = "commons_logging",
    artifact = "commons-logging:commons-logging:1.2",
    sha1 = "4bfc12adfe4842bf07b657f0369c4cb522955686",
)

maven_jar(
    name = "log_api",
    artifact = "org.slf4j:slf4j-api:1.7.2",
    sha1 = "0081d61b7f33ebeab314e07de0cc596f8e858d97",
)

maven_jar(
    name = "slf4j_simple",
    artifact = "org.slf4j:slf4j-simple:1.7.2",
    sha1 = "760055906d7353ba4f7ce1b8908bc6b2e91f39fa",
)

maven_jar(
    name = "servlet_api_3_1",
    artifact = "javax.servlet:javax.servlet-api:3.1.0",
    sha1 = "3cd63d075497751784b2fa84be59432f4905bf7c",
)

maven_jar(
    name = "commons_compress",
    artifact = "org.apache.commons:commons-compress:1.15",
    sha1 = "b686cd04abaef1ea7bc5e143c080563668eec17e",
)

maven_jar(
    name = "tukaani_xz",
    artifact = "org.tukaani:xz:1.6",
    sha1 = "05b6f921f1810bdf90e25471968f741f87168b64",
)

maven_jar(
    name = "args4j",
    artifact = "args4j:args4j:2.33",
    sha1 = "bd87a75374a6d6523de82fef51fc3cfe9baf9fc9",
)

maven_jar(
    name = "junit",
    artifact = "junit:junit:4.12",
    sha1 = "2973d150c0dc1fefe998f834810d68f278ea58ec",
)

maven_jar(
    name = "hamcrest_library",
    artifact = "org.hamcrest:hamcrest-library:1.3",
    sha1 = "4785a3c21320980282f9f33d0d1264a69040538f",
)

maven_jar(
    name = "hamcrest_core",
    artifact = "org.hamcrest:hamcrest-core:1.3",
    sha1 = "42a25dc3219429f0e5d060061f71acb49bf010a0",
)

maven_jar(
    name = "gson",
    artifact = "com.google.code.gson:gson:2.8.2",
    sha1 = "3edcfe49d2c6053a70a2a47e4e1c2f94998a49cf",
)

JETTY_VER = "9.4.11.v20180605"

maven_jar(
    name = "jetty_servlet",
    artifact = "org.eclipse.jetty:jetty-servlet:" + JETTY_VER,
    sha1 = "66d31900fcfc70e3666f0b3335b6660635154f98",
    src_sha1 = "930c50de49b9c258d5f0329426cbcac4d3143497",
)

maven_jar(
    name = "jetty_security",
    artifact = "org.eclipse.jetty:jetty-security:" + JETTY_VER,
    sha1 = "926def86d31ee07ca4b4658833dc6ee6918b8e86",
    src_sha1 = "019bc7c2a366cbb201950f24dd64d9d9a49b6840",
)

maven_jar(
    name = "jetty_server",
    artifact = "org.eclipse.jetty:jetty-server:" + JETTY_VER,
    sha1 = "58353c2f27515b007fc83ae22002feb34fc24714",
    src_sha1 = "e7d832d74df616137755996b41bc28bb82b3bc42",
)

maven_jar(
    name = "jetty_http",
    artifact = "org.eclipse.jetty:jetty-http:" + JETTY_VER,
    sha1 = "20c35f5336befe35b0bd5c4a63e07170fe7872d7",
    src_sha1 = "5bc30d1f7e8c4456c22cc85999b8cafd3741bdff",
)

maven_jar(
    name = "jetty_io",
    artifact = "org.eclipse.jetty:jetty-io:" + JETTY_VER,
    sha1 = "d164de1dac18c4ca80a1b783d879c97449909c3b",
    src_sha1 = "02c0caba292b1cb74cec1d36c6f91dc863c89b5a",
)

maven_jar(
    name = "jetty_util",
    artifact = "org.eclipse.jetty:jetty-util:" + JETTY_VER,
    sha1 = "f0f25aa2f27d618a04bc7356fa247ae4a05245b3",
    src_sha1 = "4e5c4c483cfd9804c2fc5d5751866243bbb9d740",
)
