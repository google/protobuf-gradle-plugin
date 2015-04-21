/*
 * Original work copyright (c) 2015, Alex Antonov. All rights reserved.
 * Modified work copyright (c) 2015, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.google.protobuf.gradle

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import org.gradle.api.Project

class ProtobufConvention {
    def ProtobufConvention(Project project) {
        extractedProtosDir = "${project.buildDir.path}/extracted-protos"
        generatedFileDir = "${project.buildDir}/generated-sources"
    }

    def String protocPath = "protoc"

    /**
     * Maps sourceSet names (String) -> proto source files.
     * If no value is set, generateProto tasks will use the default source
     * location: "src/${sourceSet.name}/proto", and will include all *.proto
     * files.
     * If any value is set, the default will no longer be used. Instead, the
     * values will be fed to inputs.source of the generateProto tasks.
     */
    private def Multimap<String, ?> protoSources = new ArrayListMultimap()

    /**
     * Overrides the default location of .proto files in the source. The
     * default is *.proto under 'src/${sourceSet.name}/proto'.
     *
     * <p>Example:
     * <pre>
     * protoSources 'main', fileTree('src/main/protobuf') {
     *   include '**' + '/' + '*.proto'
     *   exclude 'src/main/protobuf/excluded.proto'
     * }
     * </pre>
     */
    def protoSources(String sourceSetName, Object sourceFiles) {
      protoSources.get(sourceSetName).add(sourceFiles)
    }

    /**
     * The spec of a pre-compiled protoc plugin that is fetched from repositories.
     * It overrides 'protocPath'.
     * Spec format: '<groupId>:<artifactId>:<version>', e.g.,
     *     'com.google.protobuf:protoc:3.0.0-alpha2'
     */
    def String protocDep

    /**
     * Directory to extract proto files into
     */
    def String extractedProtosDir
		
    /**
     *	Directory to save java files to
     */
    def String generatedFileDir

    /**
     *  List of code generation plugin names and paths.
     *  -- Each item is a '<name>:<path>'
     *  -- Each name will be transformed into '--plugin=protoc-gen-<name>=<path>' and
     *     '--<name>_out=<generatedFileDir>'
     *  -- Names have to be unique
     */
    def Set protobufCodeGenPlugins = new HashSet()

    /**
     * List of native code generation plugins that is fetched from repositories.
     * -- Each itme is a '<name>:<plugin-groupId>:<plugin-artifactId>:<version>'
     * -- Each name will be transformed into '--plugin=protoc-gen-<name>=<path>' and
     *    '--<name>_out=<generatedFileDir>'
     * -- Names have to be unique
     */
    def Set protobufNativeCodeGenPluginDeps = Collections.emptySet()
}
