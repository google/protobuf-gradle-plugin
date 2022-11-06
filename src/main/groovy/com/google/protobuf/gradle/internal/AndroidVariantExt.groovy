package com.google.protobuf.gradle.internal

import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.TestVariant
import com.android.build.gradle.api.UnitTestVariant
import groovy.transform.CompileDynamic

@CompileDynamic
class AndroidVariantExt {
  private AndroidVariantExt() {
  }

  static BaseVariant getTestVariant(BaseVariant variant) {
    return variant instanceof TestVariant || variant instanceof UnitTestVariant ? variant.testedVariant : null
  }
}
