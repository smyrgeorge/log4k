package io.github.smyrgeorge.log4k.compiler.callsite

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

class CallSiteIrGenerationExtension(
    @Suppress("unused", "UNUSED_PARAMETER") configuration: CompilerConfiguration,
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val sourceFile = moduleFragment.files.firstOrNull() ?: return
        val finder = pluginContext.finderForSource(sourceFile)
        val transformer = CallSiteIrTransformer(pluginContext, finder)
        if (!transformer.isReady) return
        moduleFragment.transform(transformer, null)
    }
}
