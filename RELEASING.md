# Release process
We release protobuf-gradle-plugin to both Maven Central and Gradle Plugin Portal.

Prerequisites
--------------
1. If you haven't deployed artifacts to Maven Central or Gradle Plugin Portal before, you need to
set up your OSSRH (OSS Repository Hosting) account and Gradle Plugin Portal account.

2. Make sure your account is added to the project on Sonatype and Gradle Plugin Portal.

- Sonatype: create a support ticket on [issues.sonatype.org](issues.sonatype.org) to acquire access 
to `com.google.protobuf:protobuf-gradle-plugin` project.

- Gradle Plugin Portal: follow instructions on [this page](https://plugins.gradle.org/docs/reclaiming)
to acquire permission for publishing new versions.

3. Generate and publish GnuPG key:
- Run `gpg --full-generate-key` to generate the key.
- Run `gpg -keyring secring.gpg --export-secretkeys > ~/.gnupg/secring.gpg` to export your key.
- Run `gpg --keyserver certserver.pgp.com --send-keys <keyId>` to publish your key, where `keyId`
is the last 8 characters of the long hex from `gpg --list=keys`. You may need to prefix `keyId`
with `0x` notation.

4. Edit your Gradle user properties file (default location is `$USER_HOME/.gradle/gradle.properties`):
- Make sure `ossrhUsername` and `ossrhPassword` are added according to your Sonatype user credentials.
- Make sure `gradle.publish.key` and `gradle.publish.secret` are added according to your API key
under the "API Keys" tab in your profile page of Gradle Plugin Portal.
- Add your key information:
```
signing.keyId=<last 8 characters of the long hex from gpg --list-keys>
signing.password=<your password for signing your GPG key>
signing.secretKeyRingFile=</path/to/.gnupg/secring.gpg>
```

Releasing
----------
1. Make release commit:
- Edit `build.gradle`: 
  - remove “-SNAPSHOT” from `version`. Assuming the version is `$RELEASE_VERSION`.
- Edit `README.md`:
  - The “latest version” shown should be a version prior to `$RELEASE_VERSION`. Will refer it 
  as `$PREV_VERSION`.
  - Replace all `$PREV_VERSION` to `$RELEASE_VERSION`.
  - Update Gradle and/or Java version requirement if necessary
- Run `./gradlew clean build`.
- Run `git commit -a -m “$RELEASE_VERSION release”`.
- Run `git tag -a v$RELEASE_VERSION -m “The $RELEASE_VERSION release”`.

2. Make commit for next version:
- Refer to the next version as `$NEXT_VERSION`
- Edit `build.gradle`: change version from `$RELEASE_VERSION` to `$NEXT_VERSION-SNAPSHOT`.
- Edit `README.md`: replace `$RELEASE_VERSION-SNAPSHOT` with `$NEXT_VERSION-SNAPSHOT`.
- Run `git commit -a -m “Start $NEXT_VERSION development cycle”`

3. Publish artifacts:
- Run `git checkout v$RELEASE_VERSION`.
- Release on Maven Central:
  - Run `./gradlew uploadArchives`.
  - Go to the [OSSRH site](https://oss.sonatype.org), under “Staging Repositories”, close and release the 
  artifact.
- Release on Gradle Plugin Portal:
  - Run `./gradlew publishPlugins`.
- Verify that artifacts are available on [Maven Central](https://search.maven.org/artifact/com.google.protobuf/protobuf-gradle-plugin) 
(may take a few minutes up to hours) and [Gradle Plugin Portal](https://plugins.gradle.org/plugin/com.google.protobuf) 
(should be available immediately).

4. Push commits:
- Run `git push upstream master`.
- Run `git push --tags upstream`.

5. Create release notes.




