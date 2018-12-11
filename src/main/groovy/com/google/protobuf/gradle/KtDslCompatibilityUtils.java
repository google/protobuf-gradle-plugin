package com.google.protobuf.gradle;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.kotlin.dsl.NamedDomainObjectContainerExtensionsKt;
import org.gradle.kotlin.dsl.NamedDomainObjectContainerScope;

public class KtDslCompatibilityUtils {

    /**
     * This method is used to delegate the NamedDomainObjectContainer configuration to
     * to which ever kotlin-dsl ext implementation is available on the classpath.
     *
     * Since NamedDomainObjectContainerExtensionsKt.invoke is an inline extension function, our
     * usages in 'ProtobufConfiguratorExts.kt' would use the byte code for which ever kotlin-dsl
     * version we compiled against. This caused issues with providing compatibility with
     * kotlin-dsl 1.0.0-rc3 and 1.0.4 (Stable).
     *
     * Since the kotlin compiler creates a static implementations of all extensions, we can create a
     * delegating utility function that ensures we are not inline-ing our kotlin-dsl compilation
     * target byte code.
     *
     * @param container Container to apply the scope configuration
     * @param block A kotlin lambda to apply to the NamedDomainObjectContainerScope
     * @return A NamedDomainObjectContainer with the block lambda applied to is NamedDomainObjectContainerScope
     */
    static <T> NamedDomainObjectContainer<T> configureNamedDomainObjectContainer(
            NamedDomainObjectContainer<T> container,
            Function1<? super NamedDomainObjectContainerScope<T>, Unit> block){

        return NamedDomainObjectContainerExtensionsKt.invoke(container, block);
    }
}
